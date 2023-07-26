package syncodia.macros

import syncodia.*
import syncodia.schema.Schema.*
import munit.*
import syncodia.macros.ExtractSchema.functionSchema
import syncodia.schema.*

case class Address(city: String, country: String)

class ExtractSchemaTest extends FunSuite:

  val addressClassName: String = classOf[Address].getCanonicalName

  val ADDRESS_SCHEMA: ProductSchema =
    ProductSchema(
      addressClassName,
      List(("city", StringSchema), ("country", StringSchema))
    )

  val ADDRESS_ENTRY: (String, ProductSchema) =
    (addressClassName, ADDRESS_SCHEMA)

  case class User(name: String, age: Int, address: Address)

  val userClassName: String = classOf[User].getCanonicalName

  val USER_SCHEMA: ProductSchema = ProductSchema(
    userClassName,
    List(("name", StringSchema), ("age", IntegerSchema), ("address", ADDRESS_SCHEMA))
  )

  val USER_ENTRY: (String, ProductSchema) =
    (userClassName, USER_SCHEMA)

  case class ListNode(value: Int, next: Option[ListNode])

  val listNodeClassName: String = classOf[ListNode].getCanonicalName

  val LIST_NODE_SCHEMA: ProductSchema = ProductSchema(
    listNodeClassName,
    List(("value", IntegerSchema), ("next", OptionSchema(LIST_NODE_SCHEMA)))
  )

  val LIST_NODE_ENTRY: (String, ProductSchema) =
    (listNodeClassName, LIST_NODE_SCHEMA)

  enum Color:
    case Red, Green, Blue

  val colorClassName: String = classOf[Color].getCanonicalName

  enum ColorWithValue(val value: Int):
    case Red   extends ColorWithValue(0)
    case Green extends ColorWithValue(1)
    case Blue  extends ColorWithValue(2)

  val colorWithValueClassName: String = classOf[ColorWithValue].getCanonicalName

  enum FooBar:
    case Foo
    case Bar(i: Int)

  val fooBarClassName: String = classOf[FooBar].getCanonicalName
  val barClassName: String    = classOf[FooBar.Bar].getCanonicalName

//  enum Tree:
//    case Leaf
//    case Node(left: Tree, right: Tree)

  test("functions with no arguments") {
    def emptyFunction(): Unit = ()

    val s = functionSchema(emptyFunction _)
    val e = FunctionSchema("emptyFunction", None, List(), UnitSchema)
    assertEquals(s, e)
  }

  test("functions with one argument") {
    def functionWithOneArg(i: Int): Unit = ()

    val s = functionSchema(functionWithOneArg _)
    val e = FunctionSchema("functionWithOneArg", None, List(("i", IntegerSchema)), UnitSchema)
    assertEquals(s, e)
  }

  test("function with Boolean argument") {
    def functionWithBooleanArg(b: Boolean): Unit = ()

    val s = functionSchema(functionWithBooleanArg _)
    val e = FunctionSchema("functionWithBooleanArg", None, List(("b", BooleanSchema)), UnitSchema)
    assertEquals(s, e)
  }

  test("function with Double argument") {
    def functionWithDoubleArg(d: Double): Unit = ()

    val s = functionSchema(functionWithDoubleArg _)
    val e = FunctionSchema("functionWithDoubleArg", None, List(("d", DoubleSchema)), UnitSchema)
    assertEquals(s, e)
  }

  test("function with Long argument") {
    def functionWithLongArg(l: Long): Unit = ()

    val s = functionSchema(functionWithLongArg _)
    val e = FunctionSchema("functionWithLongArg", None, List(("l", LongSchema)), UnitSchema)
    assertEquals(s, e)
  }

  test("function with Float argument") {
    def functionWithFloatArg(f: Float): Unit = ()

    val s = functionSchema(functionWithFloatArg _)
    val e = FunctionSchema("functionWithFloatArg", None, List(("f", FloatSchema)), UnitSchema)
    assertEquals(s, e)
  }

  test("functions with two arguments") {
    def functionWithTwoArgs(i: Int, s: String): Unit = ()

    val s = functionSchema(functionWithTwoArgs _)
    val e = FunctionSchema(
      "functionWithTwoArgs",
      None,
      List(("i", IntegerSchema), ("s", StringSchema)),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("function returning a sum") {
    def functionReturningSum(a: Int, b: Int): Int = a + b

    val s = functionSchema(functionReturningSum _)
    val e =
      FunctionSchema(
        "functionReturningSum",
        None,
        List(("a", IntegerSchema), ("b", IntegerSchema)),
        IntegerSchema
      )
    assertEquals(s, e)
  }

  test("updateUser function") {
    def updateUser(user: User, newName: String): User = user.copy(name = newName)

    val s = functionSchema(updateUser _)
    val se = FunctionSchema(
      "updateUser",
      None,
      List(("user", USER_SCHEMA), ("newName", StringSchema)),
      USER_SCHEMA
    )
    assertEquals(s, se)
  }

  test("function with Seq[Int] as argument") {
    def functionWithSeqOfIntegers(ints: Seq[Int]): Unit = ()

    val s = functionSchema(functionWithSeqOfIntegers _)
    val e = FunctionSchema(
      "functionWithSeqOfIntegers",
      None,
      List(("ints", SequenceSchema("scala.collection.immutable.Seq", IntegerSchema))),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("function with List[(User, Address)] as argument") {
    def functionWithListOfTupleUsersAddresses(usersAddresses: List[(User, Address)]): Unit = ()

    val s                      = functionSchema(functionWithListOfTupleUsersAddresses _)
    val userAddressTupleSchema = TupleSchema("scala.Tuple2", List(USER_SCHEMA, ADDRESS_SCHEMA))
    val e = FunctionSchema(
      "functionWithListOfTupleUsersAddresses",
      None,
      List(
        (
          "usersAddresses",
          SequenceSchema("scala.collection.immutable.List", userAddressTupleSchema)
        )
      ),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("function with (User, Address) as argument") {
    def functionWithTupleOfUserAddress(userAddress: (User, Address)): Unit = ()

    val s                      = functionSchema(functionWithTupleOfUserAddress _)
    val userAddressTupleSchema = TupleSchema("scala.Tuple2", List(USER_SCHEMA, ADDRESS_SCHEMA))
    val e = FunctionSchema(
      "functionWithTupleOfUserAddress",
      None,
      List(("userAddress", userAddressTupleSchema)),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("function with (Int, String) as argument") {
    def functionWithTupleOfIntString(intString: (Int, String)): Unit = ()

    val s                    = functionSchema(functionWithTupleOfIntString _)
    val intStringTupleSchema = TupleSchema("scala.Tuple2", List(IntegerSchema, StringSchema))
    val e = FunctionSchema(
      "functionWithTupleOfIntString",
      None,
      List(("intString", intStringTupleSchema)),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("function returning a Seq[User]") {
    def functionReturningSeqOfUsers(users: Seq[User]): Seq[User] = users

    val s = functionSchema(functionReturningSeqOfUsers _)
    val e = FunctionSchema(
      "functionReturningSeqOfUsers",
      None,
      List(("users", SequenceSchema("scala.collection.immutable.Seq", USER_SCHEMA))),
      SequenceSchema("scala.collection.immutable.Seq", USER_SCHEMA)
    )
    assertEquals(s, e)
  }

  test("function with Option[Int] as argument") {
    def functionWithOptionOfInt(option: Option[Int]): Unit = ()

    val s = functionSchema(functionWithOptionOfInt _)
    val e =
      FunctionSchema(
        "functionWithOptionOfInt",
        None,
        List(("option", OptionSchema(IntegerSchema))),
        UnitSchema
      )
    assertEquals(s, e)
  }

  test("function returning Option[User]") {
    def functionReturningOptionOfUser(users: Seq[User]): Option[User] = users.headOption

    val s = functionSchema(functionReturningOptionOfUser _)
    val e = FunctionSchema(
      "functionReturningOptionOfUser",
      None,
      List(("users", SequenceSchema("scala.collection.immutable.Seq", USER_SCHEMA))),
      OptionSchema(USER_SCHEMA)
    )
    assertEquals(s, e)
  }

  test("function with tuple of options (Option[Int], Option[String]) as argument") {
    def functionWithTupleOfOptions(tupleOfOptions: (Option[Int], Option[String])): Unit = ()

    val s = functionSchema(functionWithTupleOfOptions _)
    val tupleSchema =
      TupleSchema("scala.Tuple2", List(OptionSchema(IntegerSchema), OptionSchema(StringSchema)))
    val e = FunctionSchema(
      "functionWithTupleOfOptions",
      None,
      List(("tupleOfOptions", tupleSchema)),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("schema for Color enum") {
    def processColor(color: Color): Unit = ()

    val s = functionSchema(processColor _)
    val e = FunctionSchema(
      "processColor",
      None,
      List(
        (
          "color",
          SumSchema(
            colorClassName,
            Map(("Red", None), ("Green", None), ("Blue", None))
          )
        )
      ),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("schema for ColorWithValue enum") {
    def processColorWithValue(color: ColorWithValue): Unit = ()

    val s = functionSchema(processColorWithValue _)
    val e = FunctionSchema(
      "processColorWithValue",
      None,
      List(
        (
          "color",
          SumSchema(
            colorWithValueClassName,
            Map(
              ("Red", None),
              ("Green", None),
              ("Blue", None)
            )
          )
        )
      ),
      UnitSchema
    )
    assertEquals(s, e)
  }

  test("schema for complex enum") {
    def processFooBar(t: FooBar): Unit = ()

    val s = functionSchema(processFooBar _)
    val e = FunctionSchema(
      "processFooBar",
      None,
      List(
        (
          "t",
          SumSchema(
            fooBarClassName,
            Map(
              ("Foo", None),
              (
                "Bar",
                Some(
                  ProductSchema(
                    barClassName,
                    List(("i", IntegerSchema)),
                    isEnum = true
                  )
                )
              )
            )
          )
        )
      ),
      UnitSchema
    )
    assertEquals(s, e)
  }

  //  test("schema for tree enum") {
  //    def processTree(t: Tree): Unit = ()
  //    val s = schemaFor(processTree _)
  //    val e = FunctionSchema("processTree", List(("t", SchemaRef("scalmus.macros.ExtractSchemaTest.Tree"))), UNIT)
  //    assertEquals(s, e)
  //    val re = Map(
  //      "scalmus.macros.ExtractSchemaTest.Tree" -> UnionSchema("scalmus.macros.ExtractSchemaTest.Tree", List(
  //      ("Leaf", SchemaRef("scalmus.macros.ExtractSchemaTest.Tree$.Leaf")),
  //      ("Node", SchemaRef("scalmus.macros.ExtractSchemaTest.Tree$.Node")
  //      )
  //    )),
  //      "scalmus.macros.ExtractSchemaTest.Tree$.Node" -> ProductSchema("scalmus.macros.ExtractSchemaTest.Tree$.Node", List(
  //        ("left", SchemaRef("scalmus.macros.ExtractSchemaTest.Tree")),
  //        ("right", SchemaRef("scalmus.macros.ExtractSchemaTest.Tree")))),
  //      "scalmus.macros.ExtractSchemaTest.Tree$.Leaf" -> ProductSchema(
  //        "scalmus.macros.ExtractSchemaTest.Tree$.Leaf",
  //        Nil
  //      )
  //    )
  //    assertEquals(r, re)
  //  }
  //
  //  test("schema for self referential case classes") {
  //    def processListNode(ln: ListNode): Unit = ()
  //    val s = schemaFor(processListNode _)
  //    val e = FunctionSchema("processListNode", None, List(("ln", SchemaRef(listNodeClassName))), UNIT)
  //    assertEquals(s, e)
  //    val re = Map(
  //      listNodeClassName -> ProductSchema(
  //        listNodeClassName,
  //        List(
  //          ("value", INTEGER),
  //          ("next", OptionSchema(SchemaRef(listNodeClassName)))
  //        )
  //      )
  //    )
  //    assertEquals(r, re)
  //  }
  //
  //  test("schema for complex nested generics") {
  //    def processComplexList(l: List[Map[String, Option[Seq[Int]]]]): Unit = ()
  //    val s = schemaFor(processComplexList _)
  //    val e = FunctionSchema(
  //      "processComplexList",
  //      List(
  //        (
  //          "l",
  //          SequenceSchema(
  //            "scala.collection.immutable.List",
  //            MapSchema(
  //              "scala.collection.immutable.Map",
  //              STRING,
  //              OptionSchema(
  //                SequenceSchema(
  //                  "scala.collection.immutable.Seq",
  //                  INTEGER
  //                )
  //              )
  //            )
  //          )
  //        )
  //      ),
  //      UNIT
  //    )
  //    assertEquals(s, e)
  //    val re = Map.empty[String, Schema]
  //    assertEquals(r, re)
  //  }

  //  test("schema for recursive Apply-type") {
  //    def processRecList(rl: RecList): Unit = ()
  //    val s = schemaFor(processRecList _)
  //    val e = FunctionSchema(
  //      "processRecList",
  //      List(
  //        (
  //          "rl",
  //          SchemaRef("scalmus.macros.ExtractSchemaTest.RecList")
  //        )
  //      ),
  //      UNIT
  //    )
  //    assertEquals(s, e)
  //    val re = Map(
  //      "scalmus.macros.ExtractSchemaTest.End$" -> ProductSchema(
  //        className = "scalmus.macros.ExtractSchemaTest.End$",
  //        fields = Nil
  //      ),
  //      "scalmus.macros.ExtractSchemaTest.Node" -> ProductSchema(
  //        className = "scalmus.macros.ExtractSchemaTest.RecListNode",
  //        fields = List(
  //          (
  //            name = "value",
  //            schema = INTEGER
  //          ),
  //          (
  //            name = "next",
  //            schema = OptionSchema(
  //              element = SchemaRef(
  //                className = "scalmus.macros.ExtractSchemaTest.RecList"
  //              )
  //            )
  //          )
  //        )
  //      ),
  //      "scalmus.macros.ExtractSchemaTest.RecList" -> SumSchema(
  //        className = "scalmus.macros.ExtractSchemaTest.RecList",
  //        fields = List(
  //          (
  //            name = "Node",
  //            schema = SchemaRef("scalmus.macros.ExtractSchemaTest.RecListNode")
  //          ),
  //          (
  //            name = "End",
  //            schema = SchemaRef("scalmus.macros.ExtractSchemaTest.End$")
  //          )
  //        )
  //      )
  //    )
  //    println(r)
  //    assertEquals(r, re)
  //  }
