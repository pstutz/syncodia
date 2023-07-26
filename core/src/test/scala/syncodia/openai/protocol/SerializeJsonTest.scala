package syncodia.openai.protocol

import munit.*
import syncodia.openai.protocol.SerializeJson.{ camelToSnake, snakeToCamel }

class SerializeJsonTest extends FunSuite:

  test("camelToSnake"):
    assertEquals(camelToSnake("camelCase"), "camel_case")
    assertEquals(camelToSnake("snakeCaseString"), "snake_case_string")
    assertEquals(camelToSnake("lowerCamelCase"), "lower_camel_case")
    assertEquals(camelToSnake("UpperCamelCase"), "upper_camel_case")

  test("snakeToCamel"):
    assertEquals(snakeToCamel("snake_case"), "snakeCase")
    assertEquals(snakeToCamel("snake_case_string"), "snakeCaseString")
    assertEquals(snakeToCamel("lower_snake_case"), "lowerSnakeCase")
    assertEquals(snakeToCamel("upper_snake_case"), "upperSnakeCase")
