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

import syncodia.openai.tokenizer.JTokkit
import syncodia.schema.FunctionSchema

case class Message(
    role: Role,
    content: String,
    name: Option[String] = None,
    functionCall: Option[FunctionCall] = None
) derives SerializeJson.ReadWriter:

  def pretty: String =
    val functionCallString: String = functionCall match
      case Some(fc) => s"call ${fc.pretty}"
      case None     => ""
    val contentString =
      if content == null || content.isEmpty then ""
      else if role == Role.Function then s"result = $content"
      else s"\"$content\""
    s"$role: $functionCallString$contentString"

  lazy val asJson: String = SerializeJson.write(this)

  lazy val tokenCount: Int = JTokkit.countTokens(asJson)
