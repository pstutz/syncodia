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

package syncodia

import syncodia.macros.ExtractSchema.functionSchema
import syncodia.schema.FunctionSchema
import syncodia.schema.Reflection

import scala.util.Failure
import scala.util.Success
import scala.util.Try

case class ChatFunction(
    name: String,
    functionSchema: ujson.Obj,
    invokeWithParams: String => (Any, String),
    resultToJson: Any => ujson.Value
)

object ChatFunction:

  inline def apply[F](inline f: F, description: String = ""): ChatFunction =
    val fs: FunctionSchema =
      if description.nonEmpty then functionSchema(f).copy(maybeDescription = Some(description))
      else functionSchema(f)

    def invokeWithParams(parametersJsonString: String): (Any, String) = Try {
      val json = ujson.read(parametersJsonString)
      fs.readFromJson(json).asInstanceOf[Seq[Any]]
    } match
      case Failure(exception) =>
        exception ->
          s"Parameter parsing failed because ${exception.getMessage}"
      case Success(parameterValues) =>
        Try(Reflection.invoke(f, fs.name, parameterValues*)) match
          case Failure(exception) =>
            exception ->
              fs.prettyFailure(parametersJsonString, exception.getMessage)
          case Success(resultValue) =>
            val resultAsJson = fs.writeToJson(resultValue)
            val prettyResult = fs.prettySuccess(parametersJsonString, resultAsJson.render())
            resultValue -> prettyResult

    new ChatFunction(fs.name, fs.asJson.obj, invokeWithParams, fs.writeToJson)
