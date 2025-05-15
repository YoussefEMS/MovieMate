// Import necessary Spark and Scala libraries
import org.apache.spark.sql.{SparkSession, Row}         // For creating Spark sessions and rows
import org.apache.spark.sql.types._                     // For defining custom data schemas (StructType, StructField, etc.)
import scala.util.{Try, Failure, Success}               // For handling exceptions with Try blocks
import scala.concurrent.{Future, Await, ExecutionContext} // For running asynchronous (parallel) API calls
import scala.concurrent.duration._                      // For specifying timeouts easily (e.g., 30.seconds)
import scala.language.postfixOps                        // Allow methods like '30.seconds' without extra imports
import ujson.Value                                       // For parsing JSON responses
import java.time.LocalDateTime                          // For generating timestamps
import java.time.format.DateTimeFormatter               // For formatting timestamps into readable strings
import scala.io.Source                                  // For reading data from URLs (HTTP requests)
import java.util.concurrent.Semaphore                   // To limit concurrent API calls (avoid overload or block)
import java.io.{PrintWriter, FileWriter}                 // For writing output files (CSV and JSON)
object main { // Start of Main object
  def main(args: Array[String]): Unit = { // Main entry point
    val apiKey = "API Key" // Define your TMDb API key (replace with real one)

    // Create a Spark session to handle data processing
    val spark = SparkSession.builder()
      .appName("Watchie") // Name the Spark application
      .master("local[*]") // Run locally using all available CPU cores
      .getOrCreate()      // Actually create and start the session
    // Define the structure (schema) of the data we'll collect
    val schema = StructType(Seq(
      StructField("title", StringType),         // Title of movie/TV show
      StructField("type", StringType),           // "movie" or "tv"
      StructField("release_year", IntegerType),  // Release year
      StructField("genres", StringType),         // Comma-separated genre names
      StructField("rating", DoubleType),          // Rating score
      StructField("director", StringType),        // Director name
      StructField("cast", StringType),            // Top 5 cast members
      StructField("platform", StringType),        // Where it's streaming (Netflix, Disney+, etc.)
      StructField("description", StringType)      // Plot overview
    ))
    // Set up the execution context to run Futures (parallel code)
    implicit val ec: ExecutionContext = ExecutionContext.global
    // Create a semaphore to control how many API requests can run at once (5 concurrent requests)
    val semaphore = new Semaphore(5)
    // -------------------------
    // Fetch genre names (movie + TV)
    // -------------------------
    val genreMap: Map[Int, String] = {
      // Helper function to fetch genre list for "movie" or "tv"
      def fetchGenres(genreType: String): Map[Int, String] = {
        val url = s"https://api.themoviedb.org/3/genre/$genreType/list?api_key=$apiKey&language=en" // Build URL
        Try(Source.fromURL(url).mkString) match { // Try downloading
          case Success(json) =>
            val parsed = ujson.read(json) // Parse JSON
            parsed("genres").arr.map(g => g("id").num.toInt -> g("name").str).toMap // Map id -> name
          case Failure(ex) =>
            println(s"Failed to fetch $genreType genres: ${ex.getMessage}") // Print error
            Map.empty[Int, String] // Return empty map
        }
      }
      fetchGenres("movie") ++ fetchGenres("tv") // Merge movie + TV genres into one map
    }
    // Helper to convert list of genre IDs into readable genre names
    def genreNames(ids: Seq[Int]): String = ids.flatMap(genreMap.get).distinct.mkString(", ")
    // -------------------------
    // Fetch director and cast info
    // -------------------------
    def fetchDirectorAndCast(mediaId: Int, mediaType: String): Future[(String, String)] = Future {
      Thread.sleep(200) // Small delay to avoid hitting API limits
      semaphore.acquire() // Block if more than 5 are running
      try {
        val url = s"https://api.themoviedb.org/3/$mediaType/$mediaId/credits?api_key=$apiKey"
        Try(Source.fromURL(url).mkString) match {
          case Success(json) =>
            val parsed = ujson.read(json)
            val director = parsed("crew").arr
              .find(_.obj.get("job").exists(_.str == "Director")) // Find first crew member whose job = Director
              .flatMap(_.obj.get("name")).map(_.str).getOrElse("Unknown") // Extract name
            val cast = parsed("cast").arr.take(5).map(_.obj("name").str).mkString(", ") // Get first 5 cast names
            (director, cast) // Return as a tuple
          case Failure(ex) =>
            println(s"Failed to fetch credits for $mediaId: ${ex.getMessage}")
            ("Unknown", "Unknown") // On failure, return unknowns
        }
      } finally {
        semaphore.release() // Always release semaphore
      }
    }
    // -------------------------
    // Fetch streaming platform info
    // -------------------------
    def fetchPlatform(mediaId: Int, mediaType: String): Future[String] = Future {
      Thread.sleep(200)
      semaphore.acquire()
      try {
        val url = s"https://api.themoviedb.org/3/$mediaType/$mediaId/watch/providers?api_key=$apiKey"
        Try(Source.fromURL(url).mkString) match {
          case Success(json) =>
            val parsed = ujson.read(json)
            parsed("results").obj.get("US") // Find US platform data
              .flatMap(obj => Try(obj("flatrate").arr.head.obj("provider_name").str).toOption)
              .getOrElse("Unknown")
          case Failure(ex) =>
            println(s"Failed to fetch platform for $mediaId: ${ex.getMessage}")
            "Unknown"
        }
      } finally {
        semaphore.release()
      }
    }
    // -------------------------
    // Fetch media items (movie or TV)
    // -------------------------
    def fetchMedia(
      mediaType: String, 
      language: String, 
      totalPages: Int, 
      originCountry: Option[String] = None, 
      startYear: Int = 1975, 
      endYear: Int = 2026
    ): Seq[Row] = {
      val baseUrl = s"https://api.themoviedb.org/3/discover/$mediaType" // Base URL
      val yearFilter = s"&primary_release_date.gte=$startYear-01-01&primary_release_date.lte=$endYear-12-31" // Filter by year
      val countryFilter = originCountry.map(c => s"&with_origin_country=$c").getOrElse("") // Optional country filter
      val languageFilter = if (language == "ar") "&with_original_language=ar" else "" // Optional Arabic filter
      val buffer = scala.collection.mutable.ListBuffer.empty[Row] // Temporary storage
      for (page <- 1 to totalPages) { // Loop over each page
        println(s"Fetching page $page of $mediaType ($language)${originCountry.map(c => s" country=$c").getOrElse("")}")
        Thread.sleep(300) // Pause slightly
        val fullUrl = s"$baseUrl?api_key=$apiKey&language=en&page=$page&sort_by=popularity.desc$languageFilter$countryFilter$yearFilter&include_adult=false&with_release_type=3|2"
        var result: Option[String] = None // Store the API result
        var retryCount = 0 // Retry attempts counter
        while (result.isEmpty && retryCount < 5) { // Retry loop (5 max)
          val response = Try(Source.fromURL(fullUrl).mkString)
          response match {
            case Success(json) => result = Some(json) // Success
            case Failure(ex) =>
              if (ex.getMessage.contains("429")) { // Rate limit
                println("Rate limit hit (429). Sleeping for 10 seconds...")
                Thread.sleep(10000) // Sleep if blocked
              } else {
                println(s"API request failed: ${ex.getMessage}")
              }
              retryCount += 1 // Increase retries
          }
        }
        result.foreach { json => // If we got a result
          val parsed = ujson.read(json)
          val resultsArray = parsed("results").arr.toList

          if (resultsArray.isEmpty) { // No results means we're done
            println(s"No more results at page $page. Stopping early.")
            return buffer.toSeq
          }
          // Fetch all items on this page
          val futures: Seq[Future[Option[Row]]] = resultsArray.map { item =>
            Future {
              Try {
                val id = Try(item("id").num.toInt).getOrElse(0)
                val title = Try(item("title").str).orElse(Try(item("name").str)).getOrElse("Unknown")
                val releaseDate = Try(item("release_date").str).orElse(Try(item("first_air_date").str)).getOrElse("0000")
                val year = Try(releaseDate.take(4).toInt).getOrElse(0)
                val rating = Try(item("vote_average").num).getOrElse(0.0)
                val description = Try(item("overview").str).getOrElse("No description available")
                val genreIds = Try(item("genre_ids").arr.map(_.num.toInt)).getOrElse(Seq())
                val genres = genreNames(genreIds)

                val rowFuture = for {
                  (director, cast) <- fetchDirectorAndCast(id, mediaType)
                  platform <- fetchPlatform(id, mediaType)
                } yield {
                  Some(Row(title, mediaType, year, genres, rating, director, cast, platform, description))
                }

                Await.result(rowFuture, 30.seconds) // Wait 30s max
              }.getOrElse(None)
            }
          }
          buffer ++= Await.result(Future.sequence(futures), 2.minutes).flatten // Add all results
        }
      }
      buffer.toSeq // Return final list
    }
    // ---------------------------------
    // Actually fetch the data
    // ---------------------------------
    val totalPages = 500 // How many pages to scrape
    val englishMovies = fetchMedia("movie", "en", totalPages) // English movies
    val englishTVShows = fetchMedia("tv", "en", totalPages)   // English TV
    val arabicEgyptianMovies = fetchMedia("movie", "ar", 500, Some("EG")) // Arabic Egyptian movies
    val arabicEgyptianTVShows = fetchMedia("tv", "ar", 500, Some("EG"))   // Arabic Egyptian TV
    val allMedia = (englishMovies ++ englishTVShows ++ arabicEgyptianMovies ++ arabicEgyptianTVShows).distinct // Combine and remove duplicates
    // Create file names with current date-time
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val fileNameCSV = "dataSet.csv"
    val fileNameJSON = "dataSet.json"
    // Escape text fields properly for CSV
    def escapeCSVField(field: Any): String = {
      val raw = field.toString
      if (raw.contains(",") || raw.contains("\"") || raw.contains("\n"))
        "\"" + raw.replace("\"", "\"\"") + "\"" // Surround by quotes and escape quotes
      else raw
    }
    // Save data as CSV file
    val rows = allMedia.map(_.toSeq.map(escapeCSVField).mkString(","))
    val writerCSV = new PrintWriter(fileNameCSV)
    writerCSV.println(schema.fieldNames.mkString(",")) // Write header
    rows.foreach(writerCSV.println) // Write each row
    writerCSV.close()
    // Save data as JSON file (organized by title)
    val writerJSON = new PrintWriter(new FileWriter(fileNameJSON))
    val jsonObjects = allMedia.map { row =>
      val rowData = schema.fieldNames.zip(row.toSeq).toMap
      val title = rowData("title")
      val fields = rowData - "title"
      s""""$title": ${ujson.Obj.from(fields.map { case (k, v) => k -> ujson.Str(v.toString) })}"""
    }
    writerJSON.println("{\n" + jsonObjects.mkString(",\n") + "\n}")
    writerJSON.close()
    println(s"Data saved to $fileNameCSV and $fileNameJSON") // Print success
    spark.stop() // Stop Spark
  }
}
