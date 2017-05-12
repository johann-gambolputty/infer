interface Type {
    val name: String
}
interface ParameterizedType : Type {
    val typeParams: List<TypeVar>
    val env: TypeEnv
    fun withEnv(env: TypeEnv): ParameterizedType
}
fun ParameterizedType.createEnv(parentEnv: BindingTypeEnv = EmptyTypeEnv()): BindingTypeEnv {
    return ParameterizedTypeEnv(typeParams.toTypedArray(), parentEnv, env)
}
fun ParameterizedType.bind(parentEnv: BindingTypeEnv, vararg types: Type): ParameterizedType {
    val env = createEnv(parentEnv)
    for (i in types.indices) {
        env.bind(typeParams[i], types[i])
    }
    return withEnv(env)
}

fun ParameterizedType.bind(vararg types: Type): ParameterizedType = this.bind(EmptyTypeEnv(), *types)

class TypeVar(val owner: Type, override val name: String) : Type {
    override fun toString(): String = "${owner.name}.$name"
    override fun equals(other: Any?): Boolean {
        return other is TypeVar && other.owner == owner && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()
}

abstract class AbstractParameterizedType(override val name: String, override val env: TypeEnv) : ParameterizedType {
    override val typeParams = mutableListOf<TypeVar>()
    fun addTypeParam(varName: String): TypeVar {
        val typeVar = TypeVar(this, varName)
        typeParams.add(typeVar)
        return typeVar
    }
    override fun equals(other: Any?): Boolean {
        return other is AbstractParameterizedType && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()

    protected fun typePrefix(): String = if (typeParams.isEmpty()) name else "$name<${typeParams.joinToString(", ")}>"
}

class TypeClassMember(val name: String, val type: Type)

class TypeClass(name: String, env: TypeEnv = EmptyTypeEnv()) : AbstractParameterizedType(name, env){
    val members = mutableListOf<TypeClassMember>()
    fun addMethod(name: String, runBuild: BuildFun.()->Unit): TypeClass {
        members.add(TypeClassMember(name, TypeFun(name, { receiver(this@TypeClass).runBuild() })))
        return this
    }
    fun findMembers(name: String) = members.filter { it.name == name }
    override fun withEnv(env: TypeEnv): ParameterizedType {
        val result = TypeClass(name, env)
        result.members.addAll(members)
        result.typeParams.addAll(typeParams)
        return result
    }
    override fun toString(): String = "${typePrefix()}$env"
    fun  withTypeParam(varName: String): TypeClass {
        addTypeParam(varName)
        return this
    }
}
class TypeFun(name: String, env: TypeEnv = EmptyTypeEnv()) : AbstractParameterizedType(name, env) {
    val params = mutableListOf<Type>()
    var returnType: Type = UNIT
    var receiver: TypeClass? = null
    fun addParam(paramType: Type): TypeFun {
        params.add(paramType)
        return this
    }
    override fun withEnv(env: TypeEnv): ParameterizedType {
        val result = TypeFun(name, env)
        result.returnType = returnType
        result.params.addAll(params)
        result.typeParams.addAll(typeParams)
        return result
    }
    override fun toString(): String = "${typePrefix()}(${params.joinToString(", ")})->$returnType$env"
    fun returns(returnType: Type) {
        this.returnType = returnType
    }

    constructor(name: String, runBuild: BuildFun.()->Unit) : this(name) {
        BuildFun(this).runBuild()
    }
}

class BuildFun(private val f: TypeFun, private val owner: TypeClass? = null) {
    fun returns(returnType: Type): BuildFun {
        f.returnType = returnType
        return this
    }
    fun param(paramType: Type): BuildFun {
        f.params.add(paramType)
        return this
    }
    fun receiver(receiverType: TypeClass): BuildFun {
        f.receiver = receiverType
        return this
    }
    fun findTypeVar(name: String): TypeVar = f.typeParams.firstOrNull { it.name == name } ?:
            f.receiver?.typeParams?.firstOrNull { it.name == name} ?:
            throw RuntimeException("Type var $name could not be found")
    fun typeVar(name: String): TypeVar = f.addTypeParam(name)
    fun params(vararg paramTypes: Type): BuildFun = params(paramTypes.toList())
    fun params(paramTypes: List<Type>): BuildFun {
        f.params.addAll(paramTypes)
        return this
    }
}
fun buildFun(name: String, runBuild: BuildFun.()->Unit): TypeFun = TypeFun(name, runBuild)

class TypeWild : Type {
    override val name: String get() = "?"
    override fun toString(): String = name
}


interface TypeEnv {
    fun resolve(typeVar: TypeVar): Type?
    fun linkTo(env: TypeEnv): TypeEnv = LinkedTypeEnv(this, env)
}

interface BindingTypeEnv : TypeEnv {
    fun bind(typeVar: TypeVar, binding: Type)
}

class EmptyTypeEnv : BindingTypeEnv {
    override fun resolve(typeVar: TypeVar): Type? = null
    override fun linkTo(env: TypeEnv): TypeEnv = env

    override fun bind(typeVar: TypeVar, binding: Type) {
        throw RuntimeException("Unknown type variable " + typeVar)
    }
    override fun toString(): String = ""
}

class LinkedTypeEnv(val env0: TypeEnv, val env1: TypeEnv) : TypeEnv {
    override fun resolve(typeVar: TypeVar): Type? = env0.resolve(typeVar) ?: env1.resolve(typeVar)

}

class ParameterizedTypeEnv(val typeParams: Array<TypeVar>, val parentEnv: BindingTypeEnv = EmptyTypeEnv(), val linkedEnv: TypeEnv = EmptyTypeEnv()) : BindingTypeEnv {
    override fun resolve(typeVar: TypeVar): Type? {
        return bindings[typeVar] ?: parentEnv.resolve(typeVar) ?: linkedEnv.resolve(typeVar)
    }

    val bindings = mutableMapOf<TypeVar, Type>()

    override fun bind(typeVar: TypeVar, binding: Type) {
        if (typeParams.contains(typeVar)) {
            bindings.put(typeVar, binding)
        }
        else {
            parentEnv.bind(typeVar, binding)
        }
    }

    override fun toString(): String {
        if (bindings.isEmpty()) {
            return ""
        }
        return bindings.entries.joinToString(", ", prefix = "{", postfix = "}", transform = { "${it.key}=${it.value}"})
    }
}
