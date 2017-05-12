
val WILD = TypeWild()
val UNIT = TypeClass("UNIT")
val STRING = TypeClass("String")
val INT = TypeClass("Int")
val LIST = TypeClass("List").withTypeParam("x")
        .addMethod("get", { returns(findTypeVar("x")).param(INT) })
val MAP = TypeClass("Map").withTypeParam("key").withTypeParam("val")


class NameLookup {
    private val names = mutableMapOf<String, List<Type>>()
    fun <T : Type> add(type: T):T {
        val types = names[type.name]?:listOf()
        names[type.name] = types + type
        return type
    }
    fun get(name: String): List<Type> = names[name]?:throw RuntimeException("Cannot resolve name '$name'")
}
val SCHEMA = NameLookup()
val ECHO = SCHEMA.add(TypeFun("echo", {
    val x = typeVar("x")
    returns(x).params(x)
}))
val LIST_OF = SCHEMA.add(TypeFun("listOf", {
    val x = typeVar("x")
    returns(LIST.bind(x)).param(x)
}))
