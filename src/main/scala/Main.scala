// MovieChatbot.scala - A rule-based chatbot for movie/TV show recommendations using Scala 3
// Uses regex and pattern matching for intent recognition

import scala.util.matching.Regex
import scala.io.Source
import scala.util.{Try, Success, Failure}
import java.io.{File, PrintWriter, FileWriter, BufferedWriter}
import scala.io.StdIn.readLine
import java.nio.file.{Files, Paths, StandardOpenOption}
import com.github.tototoshi.csv._
import java.io.File
import play.api.libs.json._
import play.api.libs.json.Reads._
import QuizLogger._
import TokenizationUtils._
enum MediaType:
  case Movie, TVShow

case class Media(
  title: String,
  mediaType: MediaType,
  year: String,
  genres: Set[String],
  rating: Double,
  director: String = "",
  cast: List[String] = List.empty,
  platform: String = "Unknown",
  overview: String = ""
)
enum ResponseType:
  case Greeting, Recommendation, SpecificMedia, QuizQuestion, QuizAnswer, Thanks, Help;
case class Response(responseType: ResponseType, message: String, recommendations: Option[Vector[Media]] = None, trivia:Option[TriviaQuestion] = None)
case class TriviaQuestion(
  question: String,
  choices: List[String],
  answer: String,
  langType: String
)

object TriviaQuestion {
  implicit val format: Format[TriviaQuestion] = Json.format[TriviaQuestion]
}

class MovieChatbot(csvFilePath: String):
  // Load media database from CSV file
  private val mediaDatabase: Vector[Media] = loadMediaDatabaseFromCsv(csvFilePath)
  
  // Load trivia questions from JSON files
  private val triviaQuestions: Map[String, List[TriviaQuestion]] = loadTriviaQuestions()
  
  // Public method to get database size
  def databaseSize: Int = mediaDatabase.size
  
  // CSV parser for the dataset format
  private def loadMediaDatabaseFromCsv(filePath: String): Vector[Media] =
    var totalLines = 0
    var skippedLines = 0
    var parsedLines = 0
    var result = Vector.empty[Media]
    
    val reader = CSVReader.open(new File(filePath))
    try
      // Skip header
      reader.readNext()
      
      // Process each row
      var row = reader.readNext()
      while row.isDefined do
        totalLines += 1
        try
          val fields = row.get
          if fields.length >= 9 then
            val title = fields(0).trim
            val mediaType = fields(1).toLowerCase match
              case "movie" => MediaType.Movie
              case "tv show" | "series" | "tv" => MediaType.TVShow
              case _ => MediaType.Movie
            
            val year = fields(2).trim  // Keep year as string
            val genres = fields(3).split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
            val rating = Try(fields(4).toDouble).getOrElse(0.0)
            val director = fields(5).trim
            val cast = fields(6).split(",").map(_.trim).filter(_.nonEmpty).toList
            val platform = fields(7).trim
            val overview = fields(8).trim
            
            val media = Media(
              title = title,
              mediaType = mediaType,
              year = year,
              genres = genres,
              rating = rating,
              director = director,
              cast = cast,
              platform = platform,
              overview = overview
            )
            
            result = result :+ media
            parsedLines += 1
          else
            skippedLines += 1
            println(s"Skipping malformed line: ${fields.mkString(",")}")
            println(s"Expected at least 9 fields, got ${fields.length}")
        catch
          case e: Exception =>
            skippedLines += 1
            println(s"Error parsing line: ${row.get.mkString(",")}")
            println(s"Exception: ${e.getMessage}")
        
        row = reader.readNext()
    finally
      reader.close()
      println(s"Total lines processed: $totalLines")
      println(s"Successfully parsed: $parsedLines")
      println(s"Skipped lines: $skippedLines")
    
    result
  
  // Helper method to parse comma-separated genres
  private def parseGenres(genresStr: String): Set[String] =
    parseList(genresStr).map(_.toLowerCase).toSet
  
  // Helper method to parse lists in CSV that are comma-separated within quotes
  private def parseList(listStr: String): List[String] =
    val trimmed = listStr.trim
    if trimmed.startsWith("\"") && trimmed.endsWith("\"") then
      trimmed.substring(1, trimmed.length - 1).split(",").map(_.trim).toList
    else
      trimmed.split(",").map(_.trim).toList
      
  // Parse CSV line with proper handling of quoted fields
  private def parseCSVLine(line: String): Array[String] =
    var result = Array.empty[String]
    var currentField = new StringBuilder
    var inQuotes = false
    
    for (c <- line) {
      if (c == '\"') {
        inQuotes = !inQuotes
      } else if (c == ',' && !inQuotes) {
        result = result :+ currentField.toString
        currentField = new StringBuilder
      } else {
        currentField.append(c)
      }
    }
    
    // Add the last field
    result = result :+ currentField.toString
    
    result

  // Consolidated pattern matching for different types of queries
  private val patterns = Map(
    "greeting" -> """(?i).*(hi|hello|hey|greetings).*""".r,
    "recommendation" -> """(?i).*(recommend|suggest|what should I watch|can you recommend).*""".r,
    "genre" -> """(?i).*(action|comedy|drama|sci-fi|thriller|horror|fantasy|crime|adventure|romance).*""".r,
    "year" -> """(?i).*?(\d{4}).*""".r,
    "rating" -> """(?i).*(highly rated|best|top|good rating|great).*""".r,
    "mediaType" -> """(?i).*(movie|film|tv show|series|tv series).*""".r,
    "specificMedia" -> """(?i).*(?:about|tell me about|what is|how is|rating of)\s+(?:the\s+)?(.+?)\??$""".r,
    "similarTo" -> """(?i).*(?:similar|like|something like)\s+(?:to\s+)?(?:the\s+)?(.+?)(?:\??|\s*$)""".r,
    "director" -> """(?i).*(?:directed by|director|films by|movies by)\s+(?:the\s+)?(.+?)\??$""".r,
    "actor" -> """(?i).*(?:starring|with actor|with actress|featuring|acted by|performance by)\s+(?:the\s+)?(.+?)\??$""".r,
    "country" -> """(?i).*(?:based in|produced in)\s+(?:the\s+)?(.+?)(?:\s|$).*""".r,
    "language" -> """(?i).*(?:in|language|spoken in)\s+(.+?)(?:\s|language|$).*""".r,
    "thanks" -> """(?i).*(thanks|thank you|thx).*""".r,
    "help" -> """(?i).*(what can you do|help|capabilities).*""".r,
    "trivia" -> """(?i).*(trivia|quiz|question|test|game).*""".r,
    "yes_trivia" -> """(?i).*(yes|yeah|sure|okay|ok|yep|yup|of course|definitely|absolutely|please|why not).*""".r,
    "no_trivia" -> """(?i).*(no|nope|nah|not really|no thanks|no thank you|that's all|that's enough|stop|quit|exit).*""".r,
    "sad" -> """(?i).*(sad|feeling down|depressed|unhappy|upset|crying|heartbroken|miserable).*""".r,
    "bored" -> """(?i).*(bored|boring|nothing to do|uninteresting|dull|monotonous|tedious).*""".r
  )

  // Helper method to format media information
  private def formatMediaInfo(media: Media): String =
    val mediaTypeStr = media.mediaType match
      case MediaType.Movie => "movie"
      case MediaType.TVShow => "TV show"
    
    val infoParts = List(
      s"${media.title} is a $mediaTypeStr ",
      if media.year.nonEmpty then s"from ${media.year}." else "",
      s"Genres: ${media.genres.mkString(", ")}.",
      s"It has a rating of ${media.rating}/10.",
      if media.director.nonEmpty && media.director != "Unknown" then s"Directed by ${media.director}." else "",
      if media.cast.nonEmpty then s"Starring ${media.cast.take(3).mkString(", ")}${if media.cast.size > 3 then " and others" else ""}." else "",
      if media.platform.nonEmpty && media.platform != "Unknown" then s"Streaming on ${media.platform}." else "",
      if media.overview.nonEmpty then s"\nOverview: ${media.overview}" else ""
    )
    
    infoParts.filter(_.nonEmpty).mkString(" ")
  private def mostLikelyMentioned(titleQuery: String, movies: Vector[Media]): Option[Media] =
    movies
      .map(movie => movie -> similarityScore(titleQuery.toLowerCase, movie.title.toLowerCase))
      .maxByOption(_._2)
      .map(_._1)

  private def similarityScore(a: String, b: String): Int =
    a.split("\\s+").count(word => b.contains(word))

  // Consolidated media filtering logic
  private def filterMedia(
    genres: Set[String] = Set.empty,
    year: Option[String] = None,
    isHighlyRated: Boolean = false,
    mediaType: Option[MediaType] = None,
    director: Option[String] = None,
    actor: Option[String] = None,
    country: Option[String] = None,
    language: Option[String] = None,
    excludeTitle: Option[String] = None
  ): Seq[Media] =
    mediaDatabase.filter(media =>
      (genres.isEmpty || genres.exists(media.genres.contains)) &&
      (year.isEmpty || media.year == year.get) &&
      (!isHighlyRated || media.rating >= 7.0) &&
      (mediaType.isEmpty || media.mediaType == mediaType.get) &&
      (director.isEmpty || media.director.toLowerCase.contains(director.get)) &&
      (actor.isEmpty || media.cast.exists(a => a.toLowerCase.contains(actor.get))) &&
      (country.isEmpty || media.platform.toLowerCase.contains(country.get)) &&
      (language.isEmpty || media.platform.toLowerCase.contains(language.get)) &&
      (excludeTitle.isEmpty || media.title != excludeTitle.get)
    ).toSeq

  private def formatMovieRecommendations(maybeMovies: Option[Vector[Media]]): String =
    def format(media: Media): String ={
      val year = media.year
      s"${media.title} ($year)"
    }
    maybeMovies match
      case Some(Vector()) => "Sorry, no movies found."
      case Some(Vector(one)) => s"I recommend you watch: ${format(one)}."
      case Some(Vector(a, b)) => s"I recommend you watch: ${format(a)} and ${format(b)}."
      case Some(movies) =>
        val allButLast = movies.init.map(format).mkString(", ")
        val last = format(movies.last)
        s"I recommend you watch: $allButLast, and $last."
      case None => "Sorry, I couldn't find any movie recommendations."


  // Main response method using pattern matching
  def respond(input: String, lastResponse: Response): Response =
    val userInput = input.toLowerCase.trim
    (userInput, lastResponse) match
      case (s, Response(ResponseType.QuizQuestion, _, _, Some(question))) =>
        {QuizLogger.logResponse(question,s); Response(ResponseType.QuizAnswer, compareAnswer(question, s))}
      case (s, _) if patterns("similarTo").findFirstMatchIn(s).isDefined =>
        val mediaName = patterns("similarTo").findFirstMatchIn(s).get.group(1)
        val possibleMedia = mediaDatabase.find(_.title.toLowerCase.contains(mediaName.toLowerCase))
        possibleMedia match
          case Some(media) =>
            val similarMovies = findSimilarMedia(Some(media))
            Response(ResponseType.Recommendation, formatMovieRecommendations(Some(similarMovies)), Some(similarMovies))
          case None =>
            Response(ResponseType.Help, s"I couldn't find '$mediaName'. Could you try with a different title?")
      case (s, Response(ResponseType.SpecificMedia, _, Some(movies), _)) if s.toLowerCase.contains("similar") || s.toLowerCase.contains("like this") =>
        val similarMovies = findSimilarMedia(movies.headOption)
        Response(ResponseType.Recommendation, formatMovieRecommendations(Some(similarMovies)), Some(similarMovies))
      case (s, Response(ResponseType.QuizAnswer,_,_,_)) if patterns("yes_trivia").findFirstMatchIn(s).isDefined =>
        getRandomQuestion() match {
          case Some(trivia) =>{
            val choices = trivia.choices.zipWithIndex.map { case (choice, idx) => 
              s"${idx + 1}. $choice"
            }.mkString("\n")
            if (trivia.langType.trim == "englishmovies" || trivia.langType.trim == "englishtvshows") {
              val randNum=scala.util.Random.nextInt(2)
              if (randNum==0) {
                Response(ResponseType.QuizQuestion, s"${trivia.question}\n\n$choices ", trivia = Some(trivia))
              } else {
                Response(ResponseType.QuizQuestion, s"${trivia.question}\n ", trivia = Some(trivia))
              }
            }
            else Response(ResponseType.QuizQuestion, s"${trivia.question}\n\n$choices", trivia = Some(trivia))
          }
          case None =>
            Response(ResponseType.Help, "Sorry, I couldn't find any trivia questions.")
        }
      case (s, Response(ResponseType.QuizAnswer,_,_,_)) if patterns("no_trivia").findFirstMatchIn(s).isDefined =>
        Response(ResponseType.Thanks, "You're welcome! Feel free to ask for more recommendations anytime.")

      
      case (s, Response(ResponseType.Recommendation, _, Some(movies), _)) if patterns("thanks").matches(s) =>
        Response(ResponseType.Thanks, "You're welcome! Feel free to ask for more recommendations anytime.")
      
      case (s, Response(ResponseType.Recommendation, _, Some(movies), _)) if patterns("specificMedia").findFirstMatchIn(s).isDefined =>
        val movie = mostLikelyMentioned(s, movies)
        movie match
          case Some(movie) => Response(ResponseType.SpecificMedia, formatMediaInfo(movie), Some(Vector(movie)))
          case None => Response(ResponseType.Help, "I couldn't find that movie. Would you like recommendations for something else?")
      
      case (s, _) if patterns("greeting").matches(s) => 
        Response(ResponseType.Greeting, "Hello! How can I help you with movies or TV shows today?")
      case (s,_) if patterns("bored").matches(s) =>
        val movies = generateRecommendation(Set("thriller"),None,true,Some(MediaType.Movie),None,None,None,None)
        Response(ResponseType.Recommendation,"Sorry to hear you're bored. Here are some highly rated thriller movies you might enjoy: \n" + formatMovieRecommendations(movies), movies)
      case (s,_) if patterns("sad").matches(s) =>
        val movies = generateRecommendation(Set("comedy"),None,true,Some(MediaType.Movie),None,None,None,None)
        Response(ResponseType.Recommendation,"Sorry to hear you're sad. Here are some highly rated comedy movies to cheer you up: \n" + formatMovieRecommendations(movies), movies)
      
      case (s, _) if patterns("recommendation").matches(s) =>
        val genres = extractGenres(s)
        val year = extractYearFromInput(s)
        val isHighlyRated = isRequestingHighlyRated(s)
        val mediaType = extractMediaType(s)
        val director = extractDirector(s)
        val actor = extractActor(s)
        val country = extractCountry(s)
        val language = extractLanguage(s)
        val Movies = generateRecommendation(genres, year, isHighlyRated, mediaType, director, actor, country, language)
        Response(ResponseType.Recommendation, formatMovieRecommendations(Movies), Movies)
      
      case (s, _) if patterns("specificMedia").findFirstMatchIn(s).isDefined =>
        val mediaName = patterns("specificMedia").findFirstMatchIn(s).get.group(1)
        Response(ResponseType.SpecificMedia, provideMediaInfo(mediaName.trim))
        
      case (s, _) if patterns("director").findFirstMatchIn(s).isDefined =>
        val director = patterns("director").findFirstMatchIn(s).get.group(1)
        val Movies = findMoviesByDirector(director.trim)
        Response(ResponseType.Recommendation, formatMovieRecommendations(Movies), Movies)
        
      case (s, _) if patterns("actor").findFirstMatchIn(s).isDefined =>
        val actor = patterns("actor").findFirstMatchIn(s).get.group(1)
        val Movies = findMoviesByActor(actor.trim)
        Response(ResponseType.Recommendation, formatMovieRecommendations(Movies), Movies)
      
      case (s, _) if patterns("thanks").matches(s) =>
        Response(ResponseType.Thanks, "You're welcome! Feel free to ask for more recommendations anytime.")

      case (s, _) if patterns("help").matches(s) =>
        Response(ResponseType.Help, """I can help you with:
          |1. Movie and TV show recommendations based on genre, year, ratings, directors, actors, country, or language
          |2. Information about specific movies or TV shows
          |3. Finding similar content to movies or shows you already enjoy
          |4. Finding movies by a specific director or actor
          |5. Movie and TV show trivia questions""".stripMargin)

      case (s, _) if patterns("trivia").matches(s) =>
        getRandomQuestion() match {
          case Some(trivia) =>{
            val choices = trivia.choices.zipWithIndex.map { case (choice, idx) => 
              s"${idx + 1}. $choice"
            }.mkString("\n")
            if (trivia.langType == "englishmovies" || trivia.langType == "englishtvshows") {
              val randNum=scala.util.Random.nextInt(2)
              if (randNum==0) {
                Response(ResponseType.QuizQuestion, s"${trivia.question}\n\n$choices", trivia = Some(trivia))
              } else {
                Response(ResponseType.QuizQuestion, s"${trivia.question}\n", trivia = Some(trivia))
              }
            }
            else Response(ResponseType.QuizQuestion, s"${trivia.question}\n\n$choices", trivia = Some(trivia))
          }
          case None =>
            Response(ResponseType.Help, "Sorry, I couldn't find any trivia questions.")
        }
      
      case (s,_) => 
        TokenizationUtils.processInput(s)
  // Helper methods to extract entities from user input
  private def extractGenres(input: String): Set[String] =
    patterns("genre").findAllMatchIn(input).map(_.group(1).toLowerCase).toSet

  private def extractYearFromInput(input: String): Option[String] =
    patterns("year").findFirstMatchIn(input).map(m => m.group(1).trim)

  private def isRequestingHighlyRated(input: String): Boolean =
    patterns("rating").findFirstIn(input).isDefined

  private def extractMediaType(input: String): Option[MediaType] =
    patterns("mediaType").findFirstMatchIn(input).map(m => 
      m.group(1).toLowerCase match
        case s if s.contains("movie") || s.contains("film") => MediaType.Movie
        case _ => MediaType.TVShow
    )
    
  private def extractDirector(input: String): Option[String] =
    patterns("director").findFirstMatchIn(input).map(_.group(1).toLowerCase)
    
  private def extractActor(input: String): Option[String] =
    patterns("actor").findFirstMatchIn(input).map(_.group(1).toLowerCase)
    
  private def extractCountry(input: String): Option[String] =
    patterns("country").findFirstMatchIn(input).map(_.group(1).toLowerCase)
    
  private def extractLanguage(input: String): Option[String] =
    patterns("language").findFirstMatchIn(input).map(_.group(1).toLowerCase)

  // Recommendation logic using consolidated filtering
  private def generateRecommendation(
    genres: Set[String], 
    year: Option[String], 
    isHighlyRated: Boolean, 
    mediaType: Option[MediaType],
    director: Option[String] = None,
    actor: Option[String] = None,
    country: Option[String] = None,
    language: Option[String] = None
  ): Option[Vector[Media]] =
    val filteredMedia = filterMedia(genres, year, isHighlyRated, mediaType, director, actor, country, language)
    
    filteredMedia match
      case Seq() => 
        None
      
      case seq if seq.length <= 2 =>
        Some(seq.toVector)
      
      case seq =>
        val recommendations = seq.filter(_.rating >= 7.0).take(3)
        if recommendations.isEmpty then
          Some(seq.sortBy(- _.rating).take(3).toVector)
        else
          Some(recommendations.toVector)

  // Provide information about specific media
  private def provideMediaInfo(mediaName: String): String =
    val possibleMatches = mediaDatabase.filter { media => 
      media.title.toLowerCase.contains(mediaName.toLowerCase) || 
      mediaName.toLowerCase.contains(media.title.toLowerCase)
    }
    
    possibleMatches match
      case Seq() => 
        s"I don't have information about $mediaName. Would you like recommendations for something else?"
      
      case Seq(media) =>
        formatMediaInfo(media)
      
      case seq =>
        val titles = seq.map(_.title).mkString(", ")
        s"I found several matches: $titles. Which one would you like to know more about?"

  // Find similar media using consolidated filtering
  private def findSimilarMedia(media: Option[Media]): Vector[Media] =
    
    media match
      case None => 
        Vector.empty
      
      case Some(media) =>
        // Calculate similarity scores for all other media
        val similar = mediaDatabase
          .filter(_.title != media.title)
          .map(m => {
            // Genre similarity (weight: 35%)
            val genreScore = (m.genres.intersect(media.genres).size.toDouble / 
              math.max(m.genres.size, media.genres.size)) * 35
            
            // Media type match (weight: 25%)
            val typeScore = if m.mediaType == media.mediaType then 25.0 else 0.0
            
            // Overview similarity (weight: 25%)
            val overviewScore = if m.overview.nonEmpty && media.overview.nonEmpty then {
              val words1 = m.overview.toLowerCase.split("\\W+").toSet
              val words2 = media.overview.toLowerCase.split("\\W+").toSet
              val commonWords = words1.intersect(words2).size.toDouble
              val totalWords = math.max(words1.size, words2.size)
              (commonWords / totalWords) * 25
            } else 0.0
            
            // Cast similarity (weight: 10%)
            val castScore = if m.cast.nonEmpty && media.cast.nonEmpty then {
              val commonCast = m.cast.map(_.toLowerCase).intersect(media.cast.map(_.toLowerCase)).size.toDouble
              val totalCast = math.max(m.cast.size, media.cast.size)
              (commonCast / totalCast) * 10
            } else 0.0
            
            // Year proximity (weight: 5%)
            val yearScore = try {
              val yearDiff = math.abs(m.year.toInt - media.year.toInt)
              val score = if yearDiff == 0 then 5.0
                         else if yearDiff <= 5 then 3.5
                         else if yearDiff <= 10 then 2.0
                         else 0.0
              score
            } catch {
              case _: NumberFormatException => 0.0
            }
            
            val totalScore = genreScore + typeScore + overviewScore + castScore //yearScore
            (m, totalScore)
          })
          .filter(_._2 > 0) // Only keep media with some similarity
          .sortBy(-_._2)    // Sort by descending similarity score
          .take(3)
          .map(_._1)
          .toSeq
        
        similar match
          case Seq() => Vector.empty
          case seq => 
            seq.toVector
            
  // Find movies by director using consolidated filtering
  private def findMoviesByDirector(directorName: String): Option[Vector[Media]] =
    val directorMovies = filterMedia(director = Some(directorName)).sortBy(- _.rating)
    
    directorMovies match
      case Seq() => None
      case seq if seq.length <= 3 => 
        Some(seq.toVector)
      case seq =>
        Some(seq.take(3).toVector)
    
  // Find movies by actor using consolidated filtering
  private def findMoviesByActor(actorName: String): Option[Vector[Media]] =
    val actorMovies = filterMedia(actor = Some(actorName)).sortBy(- _.rating)
    
    actorMovies match
      case Seq() => None
      case seq if seq.length <= 3 => 
        Some(seq.toVector)
      case seq =>
        Some(seq.take(3).toVector)

  // Load trivia questions from JSON files
  private def loadTriviaQuestions(): Map[String, List[TriviaQuestion]] = {
    val quizFiles = Map(
      "english_movies" -> "quizzes/englishmovies.json",
      "english_tvshows" -> "quizzes/englishtvshows.json",
      "arabic_movies" -> "quizzes/arabicmovies.json",
      "arabic_tvshows" -> "quizzes/arabictvshows.json"
    )
    
    quizFiles.map { case (category, file) =>
      try {
        val source = scala.io.Source.fromFile(file)
        val jsonString = try source.mkString finally source.close()
        val json = Json.parse(jsonString)
        
        // Try to parse the JSON in different formats
        val questions = Try {
          // First try parsing as an object with a "questions" field
          (json \ "questions").as[List[TriviaQuestion]]
        }.recoverWith { case _ =>
          Try {
            // Then try parsing as a direct array
            json.as[List[TriviaQuestion]]
          }
        }.getOrElse {
          println(s"Failed to parse trivia questions from $file")
          List.empty[TriviaQuestion]
        }
        
        category -> questions
      } catch {
        case e: Exception =>
          println(s"Error loading trivia file $file: ${e.getMessage}")
          category -> List.empty[TriviaQuestion]
      }
    }
  }

  private def getRandomQuestion(): Option[TriviaQuestion] = {
    val allQuestions = triviaQuestions.values.flatten.toList
    if (allQuestions.isEmpty) None
    else {
      val randomQuestion = allQuestions(scala.util.Random.nextInt(allQuestions.size))
      Some(randomQuestion)
    }
  }

  private def compareAnswer(question: TriviaQuestion, userAnswer: String): String = {
    val similarityThreshold = 0.25 // Adjust this threshold as needed
    
    // Find the index of the correct answer in the choices list
    val correctAnswerIndex = question.choices.indexWhere(_.toLowerCase.trim == question.answer.toLowerCase.trim) + 1
    
    // Try to match against the correct answer
    val answerSimilarity = answerSimilarityScore(userAnswer, question.answer, correctAnswerIndex)
    
    if (answerSimilarity >= similarityThreshold) {
      s"Correct! The answer is: ${question.answer}. Would you like another trivia question?"
    } else {
      s"Sorry, that's incorrect. The correct answer is: ${question.answer}. Would you like another trivia question?"
    }
  }


  private def answerSimilarityScore(userAnswer: String, correctAnswer: String, correctIndex: Int): Double = {
    val normalizedUser = userAnswer.trim.toLowerCase
    val normalizedCorrect = correctAnswer.trim.toLowerCase
    val tokens = normalizedUser.split("\\s+").toSet

    val numericEquivalents = Map(
      "1" -> Set("first", "one", "1st", "1", "a"),
      "2" -> Set("second", "two", "2nd", "2", "b"),
      "3" -> Set("third", "three", "3rd", "3", "c"),
      "4" -> Set("fourth", "four", "4th", "4", "d")
    )

    val isDirectMatch = normalizedUser == normalizedCorrect

    // Get the numeric equivalents for the correct index
    val correctIndexStr = correctIndex.toString
    val validAnswers = numericEquivalents.getOrElse(correctIndexStr, Set.empty)

    // Check if user answer matches any valid form of the correct index
    val isNumericEquivalent = tokens.exists(word => validAnswers.contains(word))

    if (isDirectMatch || isNumericEquivalent) 1.0
    else {
      val userWords = normalizedUser.split("\\s+").toSet
      val correctWords = normalizedCorrect.split("\\s+").toSet
      val commonWords = userWords.intersect(correctWords)
      val totalWords = userWords.union(correctWords)

      val wordOverlapScore =
        if (totalWords.nonEmpty) commonWords.size.toDouble / totalWords.size
        else 0.0

      val maxLength = math.max(normalizedUser.length, normalizedCorrect.length)
      val levenshteinScore =
        if (maxLength > 0)
          1.0 - (levenshteinDistance(normalizedUser, normalizedCorrect).toDouble / maxLength)
        else 0.0

      if (levenshteinScore >= 0.75) 1.0 // typo forgiveness
      else {
        val weightedScore = (wordOverlapScore * 0.6) + (levenshteinScore * 0.4)
        math.min(1.0, weightedScore)
      }
    }
  }

  // Helper function to calculate Levenshtein distance
  private def levenshteinDistance(s1: String, s2: String): Int = {
    val m = s1.length
    val n = s2.length
    val dp = Array.ofDim[Int](m + 1, n + 1)
    
    for (i <- 0 to m) dp(i)(0) = i
    for (j <- 0 to n) dp(0)(j) = j
    
    for (i <- 1 to m; j <- 1 to n) {
      dp(i)(j) = if (s1(i - 1) == s2(j - 1)) {
        dp(i - 1)(j - 1)
      } else {
        math.min(
          math.min(dp(i - 1)(j) + 1, dp(i)(j - 1) + 1),
          dp(i - 1)(j - 1) + 1
        )
      }
    }
    
    dp(m)(n)
  }

// Domain model for media


object MovieChatbot:
  @main def main(args: String*): Unit =
    val csvPath = args.headOption.getOrElse("data/dataSet.csv")
    println(s"Loading movie database from $csvPath...")
    
    val loadResult = Try {
      val bot = new MovieChatbot(csvPath)
      println(s"Database loaded with ${bot.databaseSize} movies!")
      
      // Setup file-based communication
      setupFileCommunication(bot)
    }
    
    loadResult match
      case Failure(exception) => 
        println(s"Error loading database: ${exception.getMessage}")
        println("Usage: run [path_to_movies.csv]")
        println("If no path is provided, 'dataSet.csv' in the current directory will be used.")
      case Success(_) => 
        println("Movie chatbot is running in file communication mode...")
  
  private def setupFileCommunication(bot: MovieChatbot): Unit = {
    // Create directory if it doesn't exist
    val connectionDir = "src/main/connection"
    new File(connectionDir).mkdirs()
    
    // Clear output file
    val outputFile = new File(s"$connectionDir/scalaoutput.txt")
    new PrintWriter(outputFile) { write(""); close() }
    
    // Create input file if it doesn't exist
    val inputFile = new File(s"$connectionDir/pyinput.txt")
    if (!inputFile.exists()) {
      new PrintWriter(inputFile) { write(""); close() }
    }
    
    var lastResponse: Response = Response(ResponseType.Greeting, "Hello! I'm your movie recommendation bot. How can I help you today?")
    
    // Setup file monitoring loop
    while (true) {
      try {
        val inputContent = Source.fromFile(inputFile).getLines().mkString("\n").trim()
        if (inputContent.nonEmpty) {
          println(s"Received input: $inputContent")
          
          // Clear input file immediately to avoid processing the same input multiple times
          new PrintWriter(inputFile) { write(""); close() }
          
          // Process input
          val response = bot.respond(inputContent, lastResponse)
          lastResponse = response
          
          // Write response to output file
          val writer = new PrintWriter(new FileWriter(outputFile))
          writer.write(response.message)
          writer.close()
          
          println(s"Sent response: ${response.message}")
        }
        
        // Pause before checking for new input
        Thread.sleep(500) 
      } catch {
        case e: Exception => 
          println(s"Error in file communication: ${e.getMessage}")
          Thread.sleep(1000) // Wait longer on error
      }
    }
  }
