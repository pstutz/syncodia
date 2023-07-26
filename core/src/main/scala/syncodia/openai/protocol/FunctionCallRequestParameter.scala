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

package syncodia.openai.protocol

import upickle.implicits.key

@key("function_call")
case class FunctionCallRequestParameter(
    @key("name")
    functionName: String
)

object FunctionCallRequestParameter:

  implicit val rw: SerializeJson.ReadWriter[FunctionCallRequestParameter] = SerializeJson
    .readwriter[Map[String, String]]
    .bimap[FunctionCallRequestParameter](
      (fcrp: FunctionCallRequestParameter) => Map("name" -> fcrp.functionName),
      (functionName: Map[String, String]) => FunctionCallRequestParameter(functionName.values.head)
    )
