package syncodia

import munit.*
import syncodia.macros.ExtractSchema.functionSchema
import syncodia.openai.protocol.FunctionCall
import syncodia.schema.Reflection.invoke

enum FooBar:
  case Foo
  case Bar(i: Int)

enum Genre:
  case Action, Drama, SciFi, Comedy, Thriller, Horror, Adventure

case class Movie(title: String, genre: Genre, director: String, releaseYear: Int, rating: Double)

case class Book(title: String, author: String, genre: Genre, pages: Int)

class InvocationTest extends FunSuite:

  test("function with primitive types") {
    val cf = ChatFunction(scala.math.multiplyExact)
    val fc = FunctionCall(
      "multiplyExact",
      """{
        "x": 2765,
        "y": 7843
      }"""
    )
    val (result, _) = cf.invokeWithParams(fc.arguments)
    assertEquals(result, 2765 * 7843)
  }

  test("square root") {
    val cf = ChatFunction(scala.math.sqrt)
    val fc = FunctionCall(
      "sqrt",
      """{
        "x": 2
      }"""
    )
    val (result, _) = cf.invokeWithParams(fc.arguments)
    assertEquals(result, math.sqrt(2))
  }

  test("get book genre") {
    def getBookGenre(book: Book): Genre = book.genre

    val cf = ChatFunction(getBookGenre)
    val fc = FunctionCall(
      "getBookGenre",
      """{
    "book": {
      "title": "Foundation",
      "author": "Isaac Asimov",
      "genre": "SciFi",
      "pages": 464
    }
  }"""
    )
    val (result, _) = cf.invokeWithParams(fc.arguments)
    assertEquals(result, Genre.SciFi)
  }

  test("identify FooBar") {
    def identifyFooBar(fb: FooBar): String = fb match
      case FooBar.Foo    => "Foo"
      case FooBar.Bar(_) => "Bar"

    val cf = ChatFunction(identifyFooBar)
    val fc = FunctionCall(
      "identifyFooBar",
      """{
      "fb": {
        "Bar": {
          "i": 5
        }
      }
    }"""
    )
    val (result, _) = cf.invokeWithParams(fc.arguments)
    assertEquals(result, "Bar")
  }

  test("switch FooBar") {
    def switchFooBar(fb: Seq[FooBar]): Seq[FooBar] =
      fb.flatMap {
        case FooBar.Foo    => Seq(FooBar.Bar(1))
        case FooBar.Bar(i) => List.fill(i)(FooBar.Foo)
      }

    val cf = ChatFunction(switchFooBar)
    val fc = FunctionCall(
      "switchFooBar",
      """{
        "fb": [
          "Foo",
          {
            "Bar": {
              "i": 2
            }
          }
        ]
      }"""
    )
    val (result, _) = cf.invokeWithParams(fc.arguments)
    val expected    = Seq(FooBar.Bar(1), FooBar.Foo, FooBar.Foo)
    assertEquals(result, expected)
  }
