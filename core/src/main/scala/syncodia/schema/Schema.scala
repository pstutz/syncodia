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

package syncodia.schema

import syncodia.schema.Schema.*
import ujson.Obj
import ujson.Value

import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag

sealed trait Schema:

  def asJson: ujson.Obj

  def pretty: String

  def readFromJson(v: ujson.Value): Any

  def writeToJson(v: Any): ujson.Value

end Schema

object Schema:

  def simplifyClassName(c: String): String = c
    .split('.')
    .last
    .stripSuffix("$")
    .stripPrefix("_")
    .stripPrefix("$")

end Schema

case object StringSchema extends Schema:

  def readFromJson(v: ujson.Value): String = v.str

  def writeToJson(v: Any): ujson.Value = ujson.Str(v.asInstanceOf[String])

  def pretty: String = "String"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("string"))

end StringSchema

case object BooleanSchema extends Schema:

  def readFromJson(v: ujson.Value): Boolean = v.bool

  def writeToJson(v: Any): ujson.Value = ujson.Bool(v.asInstanceOf[Boolean])

  def pretty: String = "Boolean"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("boolean"))

end BooleanSchema

case object IntegerSchema extends Schema:

  def readFromJson(v: ujson.Value): Int = v.num.toInt

  def writeToJson(v: Any): ujson.Value = ujson.Num(v.asInstanceOf[Int])

  def pretty: String = "Int"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("integer"), "format" -> ujson.Str("int32"))

end IntegerSchema

case object LongSchema extends Schema:

  def readFromJson(v: ujson.Value): Long = v.num.toLong

  def writeToJson(v: Any): ujson.Value = ujson.Num(v.asInstanceOf[Long].toDouble)

  def pretty: String = "Long"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("integer"), "format" -> ujson.Str("int64"))

end LongSchema

case object FloatSchema extends Schema:

  def readFromJson(v: ujson.Value): Float = v.num.toFloat

  def writeToJson(v: Any): ujson.Value = ujson.Num(v.asInstanceOf[Float])

  def pretty: String = "Float"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("number"), "format" -> ujson.Str("float"))

end FloatSchema

case object DoubleSchema extends Schema:

  def readFromJson(v: ujson.Value): Double = v.num

  def writeToJson(v: Any): ujson.Value = ujson.Num(v.asInstanceOf[Double])

  def pretty: String = "Double"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("number"), "format" -> ujson.Str("double"))

end DoubleSchema

case object UnitSchema extends Schema:

  def readFromJson(v: ujson.Value): Unit = ()

  def writeToJson(v: Any): ujson.Value = ujson.Null

  def pretty: String = "Unit"

  def asJson: ujson.Obj = ujson.Obj("type" -> ujson.Str("null"))

end UnitSchema

case class TupleSchema(className: String, elementSchemas: Seq[Schema]) extends Schema:

  // Encoded as object with keys _1, _2, ...
  def readFromJson(v: ujson.Value): Product =
    // Keys are determined based on the number of item schemas
    val keys = (1 to elementSchemas.size).map(i => s"_$i").toArray

    // Read values with the respective item schema
    val items = keys.zip(elementSchemas).map { case (k, s) => s.readFromJson(v.obj(k)) }

    // Create tuple
    Tuple.fromArray(items)

  def writeToJson(v: Any): ujson.Value =
    val items = v
      .asInstanceOf[Product]
      .productIterator
      .zip(elementSchemas)
      .zipWithIndex
      .map { case ((v, s), idx) => (s"_${idx + 1}", s.writeToJson(v)) }
    ujson.Obj.from(items)

  def pretty: String = s"Tuple[${elementSchemas.map(_.pretty).mkString(",")}]"

  def asJson: ujson.Obj = ujson.Obj(
    "type" -> "object",
    "properties" -> {
      val properties = elementSchemas.zipWithIndex.map { case (itemSchema, i) =>
        s"_$i" -> itemSchema.asJson
      }
      val props = ujson.Obj.from(properties)
      props("required") = ujson.Arr.from(properties.map(_._1))
      props
    }
  )

end TupleSchema

case class MapSchema(className: String, valueSchema: Schema) extends Schema:

  def readFromJson(v: ujson.Value): Map[String, Any] = v.obj.map { case (k, v) =>
    (k, valueSchema.readFromJson(v))
  }.toMap

  def writeToJson(v: Any): ujson.Value = ujson.Obj
    .from(v.asInstanceOf[Map[String, ?]].map { case (k, v) => (k, valueSchema.writeToJson(v)) })

  def pretty: String = s"Map[String,${valueSchema.pretty}]"

  def asJson: ujson.Obj = ujson
    .Obj("type" -> "object", "additionalProperties" -> valueSchema.asJson)

end MapSchema

case class ProductSchema(
    className: String,
    fieldSchemas: Seq[(String, Schema)],
    isEnum: Boolean = false
) extends Schema:

  def readFromJson(v: ujson.Value): Any =
    val params = fieldSchemas.map { case (k, s) => s.readFromJson(v.obj(k)) }
    if isEnum then
      val caseName      = className.split("\\$").last.stripPrefix(".")
      val enumClassName = className.substring(0, className.lastIndexOf("$"))
      Reflection.createEnumInstance(enumClassName, caseName, params*)
    else
      val cls              = Class.forName(className)
      val constructors     = ArraySeq.unsafeWrapArray(cls.getDeclaredConstructors)
      val maybeConstructor = Reflection.findExecutable(None, constructors, params*)
      maybeConstructor match
        case Some(constructor) => constructor.newInstance(params*)
        case None =>
          throw new RuntimeException(
            s"No suitable constructor found for ${cls.getSimpleName} and parameters $params."
          )

  def writeToJson(i: Any): ujson.Value =
    val items = i.asInstanceOf[Product].productIterator.zip(fieldSchemas).map { case (v, (k, s)) =>
      (k, s.writeToJson(v))
    }
    ujson.Obj.from(items)

  def pretty: String = s"${simplifyClassName(className)}"

  def asJson: ujson.Obj = ujson.Obj(
    "type"  -> ujson.Str("object"),
    "title" -> ujson.Str(pretty),
    "properties" -> {
      val properties: Seq[(String, ujson.Obj)] = fieldSchemas.map { case (name, fieldSchema) =>
        name -> fieldSchema.asJson
      }
      ujson.Obj.from(properties)
    }
  )

end ProductSchema

case class SumSchema(
    className: String,
    elementSchemas: Map[String, Option[Schema]]
) extends Schema:

  private lazy val ordinalEnums: Map[String, ?] = Reflection
    .getOrdinalEnums(className)
    .map(e => e.toString -> e)
    .toMap

  private lazy val isSimpleEnum: Boolean = ordinalEnums.size == elementSchemas.size

  def readFromJson(v: ujson.Value): Any = v match
    case ujson.Str(requiredAltName) =>
      ordinalEnums.getOrElse(
        requiredAltName,
        throw new RuntimeException(s"'$requiredAltName' is not a valid value for enum $pretty")
      )
    case o: ujson.Obj =>
      val requiredAltName = o.value.keys.head
      if ordinalEnums.contains(requiredAltName) then
        ordinalEnums.getOrElse(
          requiredAltName,
          throw new RuntimeException(s"'$requiredAltName' is not a valid value for $pretty")
        )
      else
        val maybeAltSchema = elementSchemas.get(requiredAltName).flatten
        maybeAltSchema match
          case Some(altSchema) => altSchema.readFromJson(o(requiredAltName))
          case None            => Reflection.createEnumInstance(className, requiredAltName)
    case other => throw new RuntimeException(s"Cannot read $other as $pretty")

  def writeToJson(v: Any): ujson.Value =
    val requiredAltName = v.getClass.getSimpleName
    val maybeAltSchema  = elementSchemas.get(requiredAltName).flatten
    maybeAltSchema match
      case Some(altSchema) => altSchema.writeToJson(v)
      case None            => ujson.Str(v.toString)

  def asJson: Obj =
    if isSimpleEnum then
      ujson.Obj(
        "type"  -> ujson.Str("string"),
        "title" -> ujson.Str(pretty),
        "enum"  -> ujson.Arr.from(elementSchemas.keys)
      )
    else
      ujson.Obj(
        "title" -> ujson.Str(pretty),
        "oneOf" -> ujson.Arr.from(elementSchemas.map { case (altName, maybeAltSchema) =>
          maybeAltSchema match
            case Some(altSchema) => altSchema.asJson
            case None            => ujson.Obj("title" -> ujson.Str(altName), "type" -> ujson.Str("string"))
        })
      )

  def pretty: String = s"${simplifyClassName(className)}"

end SumSchema

case class SequenceSchema(className: String, elementSchema: Schema) extends Schema:

  def asJson: Obj = ujson.Obj("type" -> ujson.Str("array"), "items" -> elementSchema.asJson)

  def pretty: String = s"Seq[${elementSchema.pretty}]"

  def readFromJson(v: Value): Any = v.arr.map(elementSchema.readFromJson).toSeq

  def writeToJson(v: Any): Value = ujson.Arr
    .from(v.asInstanceOf[Seq[?]].map(elementSchema.writeToJson))

end SequenceSchema

case class OptionSchema(element: Schema) extends Schema:

  def asJson: Obj = ujson
    .Obj("oneOf" -> ujson.Arr(ujson.Obj("type" -> ujson.Str("null")), element.asJson))

  def pretty: String = s"Option[${element.pretty}]"

  def readFromJson(v: Value): Any =
    if v.isNull then None
    else Some(element.readFromJson(v))

  def writeToJson(v: Any): Value = v match
    case None        => ujson.Null
    case Some(value) => element.writeToJson(value)

end OptionSchema

case class FunctionSchema(
    name: String,
    maybeDescription: Option[String],
    paramSchemas: Seq[(String, Schema)],
    returnType: Schema
) extends Schema:

  def pretty: String =
    val parametersString = paramSchemas.map(fs => s"${fs._1}: ${fs._2.pretty}").mkString(", ")
    s"$name($parametersString): ${returnType.pretty}"

  def prettyArgs(args: ujson.Obj): String = paramSchemas
    .map { case (name, paramSchema) =>
      s"$name = ${paramSchema.readFromJson(args(name)).toString}"
    }
    .mkString(", ")

  def prettySuccess(parametersJsonString: String, result: String): String = result

  def prettyFailure(parametersJsonString: String, errorMessage: String): String =
    s"Call with parameters '''$parametersJsonString''' failed with error '''$errorMessage'''"

  def asJson: ujson.Obj =
    val baseObj = ujson.Obj(
      "name" -> name,
      "parameters" -> ujson.Obj(
        "type" -> ujson.Str("object"),
        "properties" -> ujson.Obj.from(paramSchemas.map { case (name, parameterSchema) =>
          name -> parameterSchema.asJson
        }),
        "required" -> ujson.Arr.from(paramSchemas.map(_._1))
      ),
      "returnType" -> returnType.asJson
    )
    maybeDescription.foreach(baseObj.update("description", _))
    baseObj

  // Reads the parameters from the JSON object and returns them as a sequence of Any
  def readFromJson(v: Value): Any = paramSchemas.map { case (name, parameterSchema) =>
    parameterSchema.readFromJson(v.obj(name))
  }

  // Serialize the return value as a JSON value
  def writeToJson(v: Any): Value = returnType.writeToJson(v)

end FunctionSchema
