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

import syncodia.schema.FunctionSchema
import ujson.Value

case class FunctionCall(name: String, arguments: String) derives SerializeJson.ReadWriter:

  lazy val parsedArgs: ujson.Obj = ujson.read(arguments).obj

  def pretty: String = s"$name(${parsedArgs.value.map { case (k, v) => s"$k=$v" }.mkString(",")})"
