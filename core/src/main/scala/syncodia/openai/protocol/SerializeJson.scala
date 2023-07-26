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

import upickle.core.Visitor

import scala.util.matching.Regex

object SerializeJson extends upickle.AttributeTagged:

  private val camelToSnakeRegex: Regex = "(?<=[a-z])([A-Z])".r
  private val snakeToCamelRegex: Regex = "_([a-z])".r

  def camelToSnake(s: String): String =
    camelToSnakeRegex.replaceAllIn(s, m => "_" + m.group(1)).toLowerCase

  def snakeToCamel(s: String): String =
    snakeToCamelRegex.replaceAllIn(s, _.group(1).toUpperCase)

  override def objectAttributeKeyReadMap(s: CharSequence): String = snakeToCamel(s.toString)

  override def objectAttributeKeyWriteMap(s: CharSequence): String = camelToSnake(s.toString)

  override def objectTypeKeyReadMap(s: CharSequence): String = snakeToCamel(s.toString)

  override def objectTypeKeyWriteMap(s: CharSequence): String = camelToSnake(s.toString)

  implicit override def OptionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None    => null.asInstanceOf[T]
      case Some(x) => x
    }

    new Writer[Option[T]]:
      override def write0[V](out: Visitor[?, V], v: Option[T]): V = v match
        case None    => out.visitNull(-1)
        case Some(x) => implicitly[Writer[T]].write0(out, x)

  implicit override def OptionReader[T: Reader]: Reader[Option[T]] =
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))):

      override def visitNull(index: Int): Option[T] = None
