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

package syncodia.examples

import syncodia.*

object StreamingExample extends App:

  val syncodia = Syncodia()

  import syncodia.*

  val source = syncodia.completeStreaming("Tell me a long joke")

  source.withoutContext
    .log("error")
    .runForeach(r => print(r))
    .andThen(_ => syncodia.actorSystem.terminate())
