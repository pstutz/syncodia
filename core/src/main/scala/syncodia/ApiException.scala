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

import java.io.IOException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

enum ApiException(
    val statusCode: Int,
    val message: String,
    val maybeRetryAfter: Option[FiniteDuration]
) extends IOException(message):

  case InvalidAuthenticationException(override val message: String) extends ApiException(401, message, None)

  case IncorrectApiKeyException(override val message: String) extends ApiException(401, message, None)

  case NoMembershipException(override val message: String) extends ApiException(401, message, None)

  case RateLimitException(override val message: String) extends ApiException(429, message, Some(1000.millis))

  case QuotaExceededException(override val message: String)
      extends ApiException(429, message, Some(1000.millis))

  case ServerErrorException(override val message: String) extends ApiException(500, message, Some(100.millis))

  case OverloadedException(override val message: String) extends ApiException(503, message, Some(500.millis))

  case UnhandledException(override val statusCode: Int, override val message: String)
      extends ApiException(statusCode, message, Some(100.millis))
