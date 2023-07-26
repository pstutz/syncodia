/*
 * Copyright 2023 Justement GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package syncodia.examples

import syncodia.*
import syncodia.ChatFunction
import syncodia.Syncodia

import scala.util.*

enum Genre:
  case Action, Drama, SciFi

case class Movie(title: String, genre: Genre, director: String, releaseYear: Int, rating: Double)

object QueryExample extends App:

  def searchMovies(genre: Genre, minRating: Double, minYear: Int): Seq[Movie] =
    val movies = Seq(
      Movie("Inception", Genre.SciFi, "Christopher Nolan", 2010, 8.8),
      Movie("The Dark Knight", Genre.Action, "Christopher Nolan", 2008, 9.0),
      Movie("Interstellar", Genre.SciFi, "Christopher Nolan", 2014, 8.6),
      Movie("The Prestige", Genre.Drama, "Christopher Nolan", 2006, 8.5),
      Movie("Tenet", Genre.SciFi, "Christopher Nolan", 2020, 7.4),
      Movie("Dunkirk", Genre.Action, "Christopher Nolan", 2017, 7.9),
      Movie("The Avengers", Genre.Action, "Joss Whedon", 2012, 8.1),
      Movie("Avengers: Endgame", Genre.Action, "Anthony Russo, Joe Russo", 2019, 8.4),
      Movie("The Matrix", Genre.SciFi, "Lana Wachowski, Lilly Wachowski", 1999, 8.7),
      Movie("The Matrix Reloaded", Genre.SciFi, "Lana Wachowski, Lilly Wachowski", 2003, 7.2)
    )
    movies.filter(m => m.genre == genre && m.rating >= minRating && m.releaseYear >= minYear)

  end searchMovies

  val syncodia = Syncodia()

  import syncodia.*

  val result = syncodia
    .execute(
      "List sci-fi movies rated above 8.0 released after the year 2000.",
      functions = Seq(
        ChatFunction(
          searchMovies,
          "Searches the movie database with the given genre, minimum rating, and minimum release year."
        )
      ),
      printMessages = true
    )
    result.onComplete {
      case Success(result) => println(result.content)
      case Failure(exception) => println(exception)
    }
    result.andThen(_ => syncodia.actorSystem.terminate())
