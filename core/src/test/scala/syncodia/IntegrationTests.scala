package syncodia

import munit.FunSuite
import syncodia.util.GitHubActions
import syncodia.util.GitHubActions.isGitHubAction

import scala.compiletime.uninitialized
import scala.concurrent.ExecutionContext.Implicits.global

enum Scale:
  case Fahrenheit, Celsius

case class Temperature(degrees: Double, scale: Scale)

case class Location(lat: Double, lon: Double)

case class Weather(location: Location, temperature: Temperature)

class IntegrationTests extends FunSuite:

  override def munitIgnore: Boolean = isGitHubAction

  val apiFixture: Fixture[Syncodia] = new Fixture[Syncodia]("OpenAiApi"):
    var api: Syncodia              = uninitialized
    override def beforeAll(): Unit = if !munitIgnore then api = Syncodia()
    override def afterAll(): Unit  = if !munitIgnore then api.actorSystem.terminate()
    def apply(): Syncodia          = api
  end apiFixture

  override def munitFixtures: Seq[Fixture[?]] = List(apiFixture)

  test("basic math") {
    val api = apiFixture()
    api
      .executeContinuously(
        "Calculate the power of three of the square root of 2.",
        functions = Seq(
          ChatFunction(
            math.pow,
            "Returns the value of the first argument raised to the power of the second argument."
          ),
          ChatFunction(math.sqrt, "Returns the square root of a Double value.")
        )
      )
      .map { response =>
        assertEquals(
          response.content,
          "The power of three of the square root of 2 is approximately 2.8284271247461907."
        )
      }
  }

  test("fahrenheit to celsius conversion") {
    def fahrenheitToCelsius(fahrenheit: Temperature): Temperature =
      val degrees = (fahrenheit.degrees - 32) * 5 / 9
      Temperature(degrees, Scale.Celsius)

    val api         = apiFixture()
    val temperature = Temperature(32.0, Scale.Fahrenheit)

    val expected = ujson.Obj("degrees" -> 0, "scale" -> "Celsius")

    api
      .executeContinuously(
        s"Convert $temperature to Celsius. The response should be the exact JSON returned by `fahrenheitToCelsius` without any additional text or explanation.",
        functions = Seq(
          ChatFunction(fahrenheitToCelsius, "Converts a temperature from Fahrenheit to Celsius.")
        )
      )
      .map { response =>
        val resultJson = ujson.read(response.content)
        assertEquals(resultJson, expected)
      }
  }

  test("get location coordinates") {
    def getLocationCoordinates(location: String): Location = location.toLowerCase match
      case "new york"      => Location(40.7128, -74.006)
      case "san francisco" => Location(37.7749, -122.4194)
      case _               => throw new IllegalArgumentException(s"Unsupported location: $location")

    val api      = apiFixture()
    val location = "New York"

    val expected = ujson.Obj("lat" -> 40.7128, "lon" -> -74.006)

    api
      .executeContinuously(
        s"Get coordinates for $location. The response should be the exact JSON returned by `getLocationCoordinates` without any additional text or explanation.",
        functions = Seq(
          ChatFunction(getLocationCoordinates, "Returns coordinates for a location.")
        )
      )
      .map { response =>
        val jsonResult = ujson.read(response.content)
        assertEquals(jsonResult, expected)
      }
  }

  test("record and retrieve weather information from text") {
    def fahrenheitToCelsius(temperature: Temperature): Temperature =
      assert(temperature.scale == Scale.Fahrenheit)
      val degrees = (temperature.degrees - 32) * 5 / 9
      Temperature(degrees, Scale.Celsius)

    def getLocationCoordinates(location: String): Location = location.toLowerCase match
      case "new york" => Location(40.7128, -74.006)
      case _          => throw new IllegalArgumentException(s"Unsupported location: $location")

    def recordWeatherInfo(location: Location, temperature: Temperature): Weather =
      Weather(location, temperature)

    val api  = apiFixture()
    val text = "The weather in New York is sunny and it is 77 degrees Fahrenheit."

    val expected = ujson.Obj(
      "location"    -> ujson.Obj("lat" -> 40.7128, "lon" -> -74.006),
      "temperature" -> ujson.Obj("degrees" -> 25, "scale" -> "Celsius")
    )

    api
      .executeContinuously(
        s"Analyze this text: '$text'. Convert the temperature to Celsius and then record all found weather data using the 'recordWeatherInfo' function. Your final answer is simply the JSON representation of Weather returned by 'recordWeatherInfo'.",
        functions = Seq(
          ChatFunction(
            recordWeatherInfo,
            "Records weather information from the provided parameters and returns the recorded information."
          ),
          ChatFunction(getLocationCoordinates, "Returns coordinates for a location."),
          ChatFunction(fahrenheitToCelsius, "Converts a temperature from Fahrenheit to Celsius.")
        ),
        maxTokens = 768
      )
      .map { response =>
        val jsonResult = ujson.read(response.content)
        assertEquals(jsonResult, expected)
      }
  }
