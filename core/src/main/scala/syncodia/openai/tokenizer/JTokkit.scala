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

package syncodia.openai.tokenizer

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType

object JTokkit:

  private lazy val registry: EncodingRegistry = Encodings.newDefaultEncodingRegistry

  // cl100k_base: Used by	gpt-4, gpt-3.5-turbo, text-embedding-ada-002
  // source: https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb
  private lazy val encoding: Encoding = registry.getEncoding(EncodingType.CL100K_BASE)

  @inline
  def countTokens(s: String): Int = encoding.countTokens(s)
