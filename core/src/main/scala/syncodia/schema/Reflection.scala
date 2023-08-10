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

import java.lang.reflect.Executable
import java.lang.reflect.Method
import scala.collection.immutable.ArraySeq
import scala.language.reflectiveCalls
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.*
import scala.util.Try

object Reflection:

  private def box(c: Class[?]): Class[?] =
    import java.lang.*
    c.getName match
      case "int"     => classOf[Integer]
      case "long"    => classOf[Long]
      case "double"  => classOf[Double]
      case "float"   => classOf[Float]
      case "boolean" => classOf[Boolean]
      case "byte"    => classOf[Byte]
      case "char"    => classOf[Character]
      case "short"   => classOf[Short]
      case _         => c

  def findExecutable[T <: Executable](
      maybeName: Option[String],
      candidates: Seq[T],
      params: Any*
  ): Option[T] =
    val boxedParamClasses = params.map(p => box(p.getClass))
    val maybeExecutable: Option[T] = candidates.find { m =>
      def hasMatchingName = maybeName match
        case Some(value) => m.getName.startsWith("apply") || m.getName == value
        case None        => true
      val methodSignature           = m.getParameterTypes
      lazy val boxedMethodSignature = methodSignature.map(box)
      def sameParameterCount        = methodSignature.length == boxedParamClasses.length
      def parametersMatchSignature = boxedMethodSignature
        .zip(boxedParamClasses)
        .forall { case (methodClass, instanceClass) => methodClass.isAssignableFrom(instanceClass) }
      val found = sameParameterCount && hasMatchingName && parametersMatchSignature
      found
    }
    maybeExecutable

  def invoke(obj: Any, name: String, params: Any*): Any =
    val maybeMethod =
      findExecutable(Some(name), ArraySeq.unsafeWrapArray(obj.getClass.getDeclaredMethods), params*)
    maybeMethod match
      case Some(method) =>
        method.setAccessible(true)
        method.invoke(obj, params*)
      case None =>
        throw new NoSuchMethodException(
          s"Could not resolve function $name(${params.map(_.getClass.getName).mkString(", ")})"
        )

  def getOrdinalEnums(enumClassName: String): Array[?] =
    import reflect.Selectable.reflectiveSelectable
    val mirror       = universe.runtimeMirror(getClass.getClassLoader)
    val moduleSymbol = mirror.staticModule(enumClassName)
    val moduleMirror = mirror.reflectModule(moduleSymbol)
    val companion: { def fromOrdinal(i: Int): Any } = moduleMirror.instance
      .asInstanceOf[{ def fromOrdinal(i: Int): Any }]
    val values = LazyList
      .from(0)
      .map(i => Try(companion.fromOrdinal(i)).toOption)
      .takeWhile(_.isDefined)
      .flatten
      .toArray
    values

  def createEnumInstance(className: String, caseName: String, params: Any*): Any =
    val parent   = Class.forName(className)
    val declared = ArraySeq.unsafeWrapArray(parent.getDeclaredClasses)
    val caseEnum = declared.find(_.getSimpleName == caseName).get
    val maybeApplyMethod =
      findExecutable(Some("apply"), ArraySeq.unsafeWrapArray(caseEnum.getDeclaredMethods), params*)
    maybeApplyMethod match
      case Some(applyMethod) => applyMethod.invoke(null, params*)
      case None =>
        throw new NoSuchMethodException(
          s"Could not resolve constructor $caseName(${params.map(_.getClass.getName).mkString(", ")})"
        )
