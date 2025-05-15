import scala.math
import scala.util.Random
import Response._
import ResponseType._

object TokenizationUtils:

  // Common words and sets
  private val commonWords: List[String] = List(
    "a", "an", "as", "at", "be", "by", "do", "go", "he", "if", "in", "is", "it","me", "my", "no", "of", "on", "or", "so", "to", "up", "us", "we", "ye", "for",
    "and", "the", "but", "not", "you", "all", "any", "are", "can", "had", "has", "her","him", "his", "how", "man", "new", "now", "old", "one", "our", "out", "own", "put",
    "say", "see", "she", "two", "way", "who", "why", "yes", "yet", "you","also", "back", "best", "both", "call", "come", "each", "even", "find", "from",
    "give", "good", "have", "here", "just", "know", "like", "look", "make", "many", "more", "most", "much", "need", "only", "other", "over", "some", "such", "take",
    "tell", "than", "that", "them", "then", "they", "this", "time", "want", "well", "were", "what", "when", "will", "with", "would", "your"
  )

  // Response and emotion-related sets
  private val greetingSet: List[String] = List("hi","hey","hello","yo", "hiya", "greetings", "salutations", "aloha", "sup", "howdy","good morning","good afternoon","good evening")
  private val appreciatSet: List[String] = List("thank you","thank","thanks","thankful","grateful","appreciate")
  private val loveSet: List[String] = List("love you","love")
  private val unsatisfactSet: List[String] = List("hate","disappointed","unsatisfied","not satisfied")
  private val quizSet: List[String] = List("quiz", "test", "challenge", "question", "trivia")
  private val exitSet: List[String] = List("bye","goodbye","see you later","talk to you later","good night","nightie")
  private val phraseSet: List[String] = List("good morning","good afternoon","good evening","thank you","love you","not satisfied","see you")
  private val genreSet: List[String] = List("comedy", "crime", "action", "thriller", "adventure", "science fiction", "mystery", "animation", "drama", "horror","documentary","music")
  private val yearSet: List[String] = (1950 to 2025).map(_.toString).toList

  // Response templates
  private val greetingResponses: List[String] = List(
    "Hello! How can I help you with movies or TV shows today?",
    "Hi there! Ready to explore some great entertainment?",
    "Hey! Looking for something to watch?",
    "Greetings! What kind of movies or shows interest you?",
    "Welcome! Need help finding your next favorite movie or show?"
  )

  private val thanksResponses: List[String] = List(
    "You're welcome! Feel free to ask for more recommendations anytime.",
    "Glad I could help! Let me know if you need anything else.",
    "My pleasure! Don't hesitate to ask for more suggestions.",
    "Anytime! I'm here to help you find great entertainment.",
    "Happy to help! Come back anytime for more recommendations."
  )

  private val loveResponses: List[String] = List(
    "That's very kind! I enjoy helping you too!",
    "Aww, thanks! I love helping people find great entertainment!",
    "You're too kind! I'm just happy to help!",
    "That means a lot! I enjoy our conversations about movies and shows!",
    "Thank you! It's a pleasure helping you find great content!"
  )

  private val unsatisfiedResponses: List[String] = List(
    "I'm sorry you're not satisfied. How can I help you better?",
    "Let me try to help you in a different way. What are you looking for?",
    "I apologize for not meeting your expectations. Could you tell me more about what you're looking for?",
    "Sorry about that. Let's try a different approach to find what you need.",
    "I want to help you better. Could you tell me more about what you're interested in?"
  )

  private val quizResponses: List[String] = List(
    "Would you like to try a movie or TV show trivia question?",
    "Ready for a fun entertainment quiz?",
    "How about testing your movie and TV knowledge?",
    "Want to challenge yourself with some trivia?",
    "Shall we play a movie and TV show quiz game?"
  )

  private val exitResponses: List[String] = List(
    "Goodbye! Feel free to come back anytime for more movie recommendations!",
    "See you later! Come back when you need more entertainment suggestions!",
    "Take care! Looking forward to helping you find more great content!",
    "Bye! Don't hesitate to return for more movie and show recommendations!",
    "Until next time! Enjoy your entertainment choices!"
  )

  private val defaultResponses: List[String] = List(
    "I'm not sure I understand. You can ask for movie recommendations, information about specific shows or movies, or try a trivia question.",
    "Could you rephrase that? I can help with movie recommendations, show information, or entertainment trivia.",
    "I didn't quite catch that. Try asking about specific movies, shows, or maybe start a trivia game!",
    "I'm here to help with movies and shows! What would you like to know?",
    "Not sure what you're looking for. Want to try asking about movies, shows, or taking a quiz?"
  )

  // Getter methods for the sets
  def getGreetingSet: List[String] = greetingSet
  def getAppreciatSet: List[String] = appreciatSet
  def getLoveSet: List[String] = loveSet
  def getUnsatisfactSet: List[String] = unsatisfactSet
  def getQuizSet: List[String] = quizSet
  def getExitSet: List[String] = exitSet
  def getPhraseSet: List[String] = phraseSet
  def getGenreSet: List[String] = genreSet
  def getYearSet: List[String] = yearSet

  /**
   * Process input text and return a Response based on tokenization analysis
   * @param input The input text to process
   * @return Response with appropriate response type and message
   */
  def processInput(input: String): Response =
    val tokens = tokenize(input.toLowerCase)
    val phrases = identifyPotentialTitleOrName(tokens)
    
    // Check for greetings
    if tokens.exists(token => greetingSet.contains(token)) || phrases.exists(phrase => greetingSet.contains(phrase)) then
      Response(ResponseType.Greeting, greetingResponses(Random.nextInt(greetingResponses.length)))
    // Check for appreciation
    else if tokens.exists(token => appreciatSet.contains(token)) || phrases.exists(phrase => appreciatSet.contains(phrase)) then
      Response(ResponseType.Thanks, thanksResponses(Random.nextInt(thanksResponses.length)))
    // Check for love expressions
    else if tokens.exists(token => loveSet.contains(token)) || phrases.exists(phrase => loveSet.contains(phrase)) then
      Response(ResponseType.Thanks, loveResponses(Random.nextInt(loveResponses.length)))
    // Check for dissatisfaction
    else if tokens.exists(token => unsatisfactSet.contains(token)) || phrases.exists(phrase => unsatisfactSet.contains(phrase)) then
      Response(ResponseType.Help, unsatisfiedResponses(Random.nextInt(unsatisfiedResponses.length)))
    // Check for quiz/trivia interest
    else if tokens.exists(token => quizSet.contains(token)) then
      Response(ResponseType.Help, quizResponses(Random.nextInt(quizResponses.length)))
    // Check for exit phrases
    else if tokens.exists(token => exitSet.contains(token)) || phrases.exists(phrase => exitSet.contains(phrase)) then
      Response(ResponseType.Thanks, exitResponses(Random.nextInt(exitResponses.length)))
    // Default response for unrecognized input
    else
      Response(ResponseType.Help, defaultResponses(Random.nextInt(defaultResponses.length)))

  /**
   * Tokenizes input text and removes common words
   * @param txt The input text to tokenize
   * @return List of tokenized words with common words removed
   */
  def tokenize(txt: String): List[String] =
    val txt2 = txt
      .toLowerCase                        // Convert all text to lowercase
      .replaceAll("n't", " not")         // Expand contractions (e.g., "don't" → "do not")
      .replaceAll("'m", " am")           // "I'm" → "I am"
      .replaceAll("'s", " is")           // "he's" → "he is"
      .replaceAll("'re", " are")         // "they're" → "they are"
      
    txt2
      .replaceAll("[^a-zA-Z0-9\\s]", "") // Remove punctuation/special characters
      .split("\\s+")                     // Split by whitespace
      .filter(_.nonEmpty)                // Remove empty strings
      .toList                            // Convert to List
      .filter(token => !commonWords.contains(token))

  /**
   * Parses input text into meaningful tokens
   * @param input The input text to parse
   * @return List of meaningful tokens (genres, years, potential actors)
   */
  def parseInput(input: String): List[String] =
    val allTokens: List[String] = tokenize(input)
    
    // Check for genres
    val genreTokens = allTokens.filter(token => genreSet.contains(token.toLowerCase))
    
    // Check for years (4-digit numbers)
    val yearTokens = allTokens.filter(token => token.matches("\\d{4}"))
    
    // Check for potential actor names (words that aren't genres, years, or common words)
    val potentialActors = allTokens.filter(token => 
      !genreSet.contains(token) && 
      !token.matches("\\d{4}") &&
      token.length > 2
    )
    
    (genreTokens ++ yearTokens ++ potentialActors).distinct

  /**
   * Calculate Levenshtein distance between two strings
   * @param s1 First string
   * @param s2 Second string
   * @return The edit distance between the strings
   */
  def levenshteinDistance(s1: String, s2: String): Int =
    val dist = Array.ofDim[Int](s1.length + 1, s2.length + 1)
    
    for i <- 0 to s1.length do dist(i)(0) = i
    for j <- 0 to s2.length do dist(0)(j) = j
    
    for
      j <- 1 to s2.length
      i <- 1 to s1.length
    do
      val cost = if s1(i-1) == s2(j-1) then 0 else 1
      dist(i)(j) = math.min(math.min(
        dist(i-1)(j) + 1,     // deletion
        dist(i)(j-1) + 1),    // insertion
        dist(i-1)(j-1) + cost // substitution
      )
    
    dist(s1.length)(s2.length)

  /**
   * Calculate similarity ratio between two strings
   * @param s1 First string
   * @param s2 Second string
   * @return Similarity ratio between 0.0 and 1.0
   */
  def similarityRatio(s1: String, s2: String): Double =
    val maxLength = math.max(s1.length, s2.length)
    if maxLength == 0 then 1.0 else (maxLength - levenshteinDistance(s1, s2)).toDouble / maxLength

  /**
   * Find best fuzzy matches from a list of candidates
   * @param query The string to match
   * @param candidates List of candidate strings to match against
   * @param threshold Minimum similarity ratio to consider a match
   * @return List of matches above the threshold
   */
  def findBestFuzzyMatches(query: String, candidates: List[String], threshold: Double): List[String] =
    if query.length < 3 then return List() 
    
    val similarities = candidates.map(candidate => (candidate, similarityRatio(query.toLowerCase, candidate.toLowerCase)))
    
    val matches = similarities
      .filter(_._2 >= threshold)
      .sortBy(-_._2)
      .map(_._1)
    
    if matches.isEmpty then List(query) else matches

  /**
   * Identify potential multi-word titles or names
   * @param tokens List of tokens to analyze
   * @return List of potential multi-word phrases
   */
  def identifyPotentialTitleOrName(tokens: List[String]): List[String] =
    if tokens.length < 2 then return List()
    
    // For pairs of tokens
    val potentialPhrases = tokens.sliding(2).flatMap { pair =>
      if !commonWords.contains(pair(0)) && 
         !commonWords.contains(pair(1)) && 
         pair(0).length > 2 && 
         pair(1).length > 2 then
        
        val phrase = pair.mkString(" ")
        val fuzzyMatches = findBestFuzzyMatches(phrase, phraseSet, 0.8)
        
        List(phrase) ++ fuzzyMatches
      else
        List()
    }.toList
    
    // For triplets of tokens
    val tripleTokenPhrases = if tokens.length >= 3 then
      tokens.sliding(3).flatMap { triple =>
        if !commonWords.contains(triple(0)) && 
           !commonWords.contains(triple(2)) && 
           triple(0).length > 2 && 
           triple(2).length > 2 then
          
          val phrase = triple.mkString(" ")
          val fuzzyMatches = findBestFuzzyMatches(phrase, phraseSet, 0.8)
          
          List(phrase) ++ fuzzyMatches
        else
          List()
      }.toList
    else List()
    
    potentialPhrases ++ tripleTokenPhrases