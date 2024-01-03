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

type ChatCompletionModel = String

object ChatCompletionModel:

  lazy val GPT_4 = "gpt-4"

  lazy val GPT_4_0613 = "gpt-4-0613"

  lazy val GPT_4_TURBO = "gpt-4-1106-preview"

  lazy val GPT_4_32K = "gpt-4-32k"

  lazy val GPT_4_32K_0613 = "gpt-4-32k-0613"

  lazy val GPT_35_TURBO = "gpt-3.5-turbo"

  lazy val GPT_35_TURBO_16K = "gpt-3.5-turbo-16k"

  lazy val GPT_35_TURBO_0613 = "gpt-3.5-turbo-0613"

  lazy val GPT_35_TURBO_16K_0613 = "gpt-3.5-turbo-16k-0613"
