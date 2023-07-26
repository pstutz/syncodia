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

package syncodia.macros

import syncodia.schema.*

import scala.annotation.tailrec
import scala.compiletime.summonFrom

object ExtractSchema:

  import scala.quoted.*

  inline def functionSchema[T](inline f: T): FunctionSchema = ${ functionSchemaImpl('f) }

  private def functionSchemaImpl[F](f: Expr[F])(using Quotes, Type[F]): Expr[FunctionSchema] =
    import quotes.reflect.*

    @tailrec
    def extractFunctionName(t: Term): String = t match
      case Lambda(_, e)                     => extractFunctionName(e)
      case DefDef(_, _, _, Some(applyTree)) => extractFunctionName(applyTree)
      case Block(List(e: Term), _)          => extractFunctionName(e)
      case Inlined(_, _, e)                 => extractFunctionName(e)
      case Apply(Ident(fn), _)              => fn
      case Apply(Select(_, fn), _)          => fn
      case _ => report.errorAndAbort(s"Not a function: ${t.show(using Printer.TreeStructure)}", f)

    end extractFunctionName

    @tailrec
    def extractParamNames(t: Term): List[String] = t match
      case Lambda(_, e)                                   => extractParamNames(e)
      case DefDef(_, List(TermParamClause(params)), _, _) => params.map(_.name)
      case Block(List(e: Term), _)                        => extractParamNames(e)
      case Inlined(_, _, e)                               => extractParamNames(e)
      case Apply(_, params: List[Ident @unchecked])       => params.map(_.name)
      case _ =>
        report
          .errorAndAbort(s"No paramSchemas found: ${t.show(using Printer.TreeStructure)}", f)
    end extractParamNames

    val tree                     = f.asTerm
    val fnName                   = extractFunctionName(tree)
    val paramNames: List[String] = extractParamNames(tree)
    val repr                     = TypeRepr.of[F]
    val allTypeArgs              = repr.typeArgs
    val paramTypes               = allTypeArgs.init
    val returnType               = allTypeArgs.last
    val paramSchemas = paramNames
      .zip(paramTypes)
      .foldLeft(List[(String, Schema)]()) { case (paramAcc, (name, tpe)) =>
        val fieldSchema = extractSchema(tpe)
        paramAcc :+ name -> fieldSchema
      }
    val returnTypeSchema = extractSchema(returnType)

    Expr(FunctionSchema(fnName, None, paramSchemas, returnTypeSchema))

  end functionSchemaImpl

  def extractSchema(using quotes: Quotes)(tpe: quotes.reflect.TypeRepr): Schema =
    import quotes.reflect.*

    val dealiasedTpe = tpe.dealias
    val ts           = dealiasedTpe.typeSymbol
    val className    = ts.fullName

    def isTuple: Boolean = ts.flags.is(Flags.Case) && className.startsWith("scala.Tuple")
    def isEnum: Boolean  = tpe <:< TypeRepr.of[scala.reflect.Enum]

    def stringRepresentationOfType =
      s"${tpe.typeSymbol.name}${tpe.typeArgs.map(_.typeSymbol.name).mkString("[", ",", "]")}"

    dealiasedTpe.asType match
      case '[String]         => StringSchema
      case '[Boolean]        => BooleanSchema
      case '[Int]            => IntegerSchema
      case '[Long]           => LongSchema
      case '[Float]          => FloatSchema
      case '[Double]         => DoubleSchema
      case '[Unit]           => UnitSchema
      case '[Seq[t]]         => SequenceSchema(className, extractSchema(TypeRepr.of[t]))
      case '[Option[t]]      => OptionSchema(extractSchema(TypeRepr.of[t]))
      case '[Map[String, t]] => MapSchema(className, extractSchema(TypeRepr.of[t]))
      case _ if isTuple => // Tuple
        val typeArgs    = dealiasedTpe.typeArgs
        val itemSchemas = typeArgs.map(extractSchema(_))
        TupleSchema(className, itemSchemas)

      case _ if ts.flags.is(Flags.Case) => // Case class
        val fields = dealiasedTpe.typeSymbol.caseFields
        val fieldSchemas = fields.zip(fields.map(_.tree)).collect { case (ft, vd: ValDef) =>
          ft.name -> extractSchema(vd.tpt.tpe)
        }
        ProductSchema(className, fieldSchemas, isEnum)

      case _ if isEnum && ts.flags.is(Flags.Abstract) => // Enum
        val children   = dealiasedTpe.typeSymbol.children
        val childTrees = children.map(_.tree)
        val alternatives: Map[String, Option[Schema]] = children
          .zip(childTrees)
          .collect {
            case (c, tpd: Typed) => c.name -> Some(extractSchema(tpd.tpt.tpe))
            case (c, vd: ValDef) =>
              val childTpe = vd.tpt.tpe
              if childTpe == tpe then c.name -> None
              else
                val childSchema = extractSchema(childTpe)
                c.name -> Some(childSchema)
            case (c, cd: ClassDef) => c.name -> Some(extractSchema(cd.constructor.returnTpt.tpe))
            case (_, other) =>
              report.errorAndAbort(
                s"Unsupported schema extraction for enum $stringRepresentationOfType:\n$other"
              )
          }
          .toMap
        SumSchema(className, alternatives)
      case _ =>
        report
          .errorAndAbort(s"Unsupported schema extraction for $stringRepresentationOfType.")

  end extractSchema

  // Boilerplate for ToExpr
  given ToExpr[Schema] with

    def apply(s: Schema)(using Quotes): Expr[Schema] = s match
      case StringSchema      => Expr(StringSchema)
      case BooleanSchema     => Expr(BooleanSchema)
      case IntegerSchema     => Expr(IntegerSchema)
      case LongSchema        => Expr(LongSchema)
      case FloatSchema       => Expr(FloatSchema)
      case DoubleSchema      => Expr(DoubleSchema)
      case UnitSchema        => Expr(UnitSchema)
      case s: ProductSchema  => Expr(s)
      case s: TupleSchema    => Expr(s)
      case s: SumSchema      => Expr(s)
      case s: SequenceSchema => Expr(s)
      case s: OptionSchema   => Expr(s)
      case s: MapSchema      => Expr(s)
      case other             => throw new Exception(s"Unsupported schema: $other")

  given ToExpr[StringSchema.type] with
    def apply(s: StringSchema.type)(using Quotes): Expr[StringSchema.type] = '{ StringSchema }

  given ToExpr[BooleanSchema.type] with
    def apply(s: BooleanSchema.type)(using Quotes): Expr[BooleanSchema.type] = '{ BooleanSchema }

  given ToExpr[IntegerSchema.type] with
    def apply(s: IntegerSchema.type)(using Quotes): Expr[IntegerSchema.type] = '{ IntegerSchema }

  given ToExpr[LongSchema.type] with
    def apply(s: LongSchema.type)(using Quotes): Expr[LongSchema.type] = '{ LongSchema }

  given ToExpr[FloatSchema.type] with
    def apply(s: FloatSchema.type)(using Quotes): Expr[FloatSchema.type] = '{ FloatSchema }

  given ToExpr[DoubleSchema.type] with
    def apply(s: DoubleSchema.type)(using Quotes): Expr[DoubleSchema.type] = '{ DoubleSchema }

  given ToExpr[UnitSchema.type] with
    def apply(s: UnitSchema.type)(using Quotes): Expr[UnitSchema.type] = '{ UnitSchema }

  given ToExpr[ProductSchema] with
    def apply(s: ProductSchema)(using Quotes): Expr[ProductSchema] =
      '{ ProductSchema(${ Expr(s.className) }, ${ Expr(s.fieldSchemas) }, ${ Expr(s.isEnum) }) }

  given ToExpr[TupleSchema] with
    def apply(s: TupleSchema)(using Quotes): Expr[TupleSchema] =
      '{ TupleSchema(${ Expr(s.className) }, ${ Expr(s.elementSchemas) }) }

  given ToExpr[SumSchema] with
    def apply(s: SumSchema)(using Quotes): Expr[SumSchema] =
      '{ SumSchema(${ Expr(s.className) }, ${ Expr(s.elementSchemas) }) }

  given ToExpr[SequenceSchema] with
    def apply(s: SequenceSchema)(using Quotes): Expr[SequenceSchema] =
      '{ SequenceSchema(${ Expr(s.className) }, ${ Expr(s.elementSchema) }) }

  given ToExpr[OptionSchema] with
    def apply(s: OptionSchema)(using Quotes): Expr[OptionSchema] =
      '{ OptionSchema(${ Expr(s.element) }) }

  given ToExpr[MapSchema] with
    def apply(s: MapSchema)(using Quotes): Expr[MapSchema] =
      '{ MapSchema(${ Expr(s.className) }, ${ Expr(s.valueSchema) }) }

  given ToExpr[FunctionSchema] with
    def apply(f: FunctionSchema)(using Quotes): Expr[FunctionSchema] = '{
      FunctionSchema(
        ${ Expr(f.name) },
        ${ Expr(None) },
        ${ Expr.ofList(f.paramSchemas.map(fs => Expr(fs))) },
        ${ Expr(f.returnType) }
      )
    }
