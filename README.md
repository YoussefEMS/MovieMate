# Movie Chatbot with Trivia

A Scala-based interactive rule-based chatbot that provides movie and TV show recommendations along with an engaging trivia game feature. The chatbot uses natural language processing to understand user queries and provides personalized recommendations based on various criteria such as genre, year, rating, and more.

## Features

- Movie and TV show recommendations based on:
  - Genre
  - Year
  - Rating
  - Director
  - Actor/Actress
  - Similar content
- Comprehensive trivia game with questions about:
  - English Movies
  - English TV Shows
  - Arabic Movies
  - Arabic TV Shows
- Natural language understanding for user queries
- Intelligent answer matching for trivia questions
- Logging system for quiz responses
- Interactive web interface with analytics dashboard

## Data

- The dataset is collected from The Movie Database (TMDb) API and includes comprehensive information about movies and TV shows in both English and Arabic. The data is stored in CSV.
- Each entry in the dataset contains the following fields:

| Field | Type | Description |
|-------|------|-------------|
| title | String | Name of the movie or TV show |
| type | String | Either "movie" or "tv" |
| release_year | Integer | Year of release (1975-2026) |
| genres | String | Comma-separated list of genres |
| rating | Double | Average rating score (0-10) |
| director | String | Name of the director |
| cast | String | Top 5 cast members, comma-separated |
| platform | String | Streaming platform availability |
| description | String | Plot overview |

### Data Categories

The dataset includes four main categories:
1. English Movies
2. English TV Shows
3. Arabic Egyptian Movies
4. Arabic Egyptian TV Shows

### Data Quality

- All text fields are properly escaped
- Missing values are handled gracefully
- Duplicate entries are removed
- Data is validated before storage

## Prerequisites

- Java Development Kit (JDK) 11 or higher
- sbt (Scala Build Tool) 1.0 or higher
- Python 3.x
- pip (Python package manager)

## Installation

1. Clone the repository:
   ```bash
   git clone [https://github.com/YoussefEMS/MovieMate](https://github.com/YoussefEMS/MovieMate)
   cd MovieMate
   ```

2. Install sbt dependencies:
   ```bash
   sbt update
   ```

3. Install Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```

4. Ensure you have the required data files:
   - Place your movie dataset in `data/dataSet.csv`
   - Verify quiz files are present in `quizzes/` directory:
     - englishmovies.json
     - englishtvshows.json
     - arabicmovies.json
     - arabictvshows.json

## Project Structure

```
.
├── build.sbt                # Scala build configuration
├── src/
│   ├── main/
│   │   ├── scala/         # Scala source files
│   │   └── connection/    # Communication files between Python and Scala
│   └── app.py            # Python UI application
├── data/                   # Movie dataset
├── quizzes/               # Trivia question files
├── requirements.txt       # Python dependencies
└── quiz_responses.csv     # Log of quiz responses
```

## Running the Application

1. Start the Scala backend:
   ```bash
   sbt run
   ```
   By default, it will use `data/dataSet.csv`. You can specify a different dataset path as an argument:
   ```bash
   sbt "run path/to/your/dataset.csv"
   ```

2. In a new terminal, start the Python web interface:
   ```bash
   streamlit run src/app.py
   ```
   This will open a web browser with the interactive UI. If it doesn't open automatically, visit:
   ```
   http://localhost:8501
   ```

3. The chatbot supports various types of queries:
   - "Recommend me a comedy movie"
   - "What movies are similar to Inception?"
   - "Tell me about The Matrix"
   - "Let's play a trivia game"
   - "What can you do?"

## Web Interface Features

- Interactive chat interface
- Real-time movie and TV show recommendations
- Trivia game with multiple-choice questions
- Analytics dashboard showing:
  - Overall quiz performance
  - Performance by category (English/Arabic, Movies/TV Shows)
  - Time-based analysis
  - Daily performance tracking

## Trivia Game

The trivia system features questions about movies and TV shows in both English and Arabic. When playing:
- Questions can be answered by:
  - Typing the full answer
  - Using the option number (1-4)
  - Using number words ("one", "two", etc.)
  - Using ordinals ("first", "second", etc.)

## Dependencies

### Scala Dependencies
- Scala 3.7.0
- scala-csv 1.3.10 (for CSV parsing)
- play-json 2.10.0-RC7 (for JSON handling)
- munit 1.0.0 (for testing)

### Python Dependencies
- streamlit >= 1.24.0 (for web interface)
- pandas >= 1.5.3 (for data handling)
- plotly >= 5.13.0 (for analytics visualizations)
- requests >= 2.28.2 (for HTTP requests)


## Team
- Youssef Ihab
- Ahmed Yasser
- Youssef Ashoush
- Youssef Wael


