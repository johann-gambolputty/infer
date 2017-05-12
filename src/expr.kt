

interface Expr {
}

class ExprConst(val constType: Type, val constValue: String) : Expr {
    override fun toString(): String = constValue
}
fun xconst(constType: Type, constValue: String) = ExprConst(constType, constValue)
class ExprDispatch(val receiver: Expr, vararg val args: Expr) : Expr {
    override fun toString(): String = "$receiver(${args.joinToString()})"
}
fun xdispatch(receiver: Expr, vararg args: Expr) = ExprDispatch(receiver, *args)
class ExprAccess(val source: Expr, val name: String): Expr {
    override fun toString(): String = "$source.$name"
}
fun xget(source: Expr, name: String) = ExprAccess(source, name)
class ExprRef(val name: String): Expr {
    override fun toString(): String = name
}
fun xref(name: String) = ExprRef(name)

class CodeWriter {
    private var prefix = ""
    private val sb = StringBuilder()
    fun indent() {
        prefix = prefix + "\t"
    }
    fun print(s: String): CodeWriter {
        sb.append(prefix).append(s).append("\n")
        return this
    }
    fun print(e: TypedExpr): CodeWriter = e.toSource(this)
    fun undent() {
        prefix = prefix.substring(0, prefix.length-1)
    }

    override fun toString(): String {
        return sb.toString()
    }
}

class CallSignature(val signature: TypeFun, val apply:(sourceExpr: ExprDispatch, receiver: TypedExpr, args: List<TypedExpr>)->TypedExpr?)

interface TypedExpr {
    val sourceExpr: Expr
    val type: Type
    fun getCallSignatures(): List<CallSignature>
    fun toSource(cw: CodeWriter): CodeWriter
    fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr?
}
fun TypedExpr.unify(): TypedExpr? = unify(TypeWild(), EmptyTypeEnv(), EmptyTypeEnv(), EmptyTypeEnv())

class Operator(val signature: TypeFun) {
}

fun dispatchOperator(type: Type): Operator? {
    if (type !is TypeFun) {
        return null
    }
    return Operator(type)
}

class ConstTypedExpr(val expr: ExprConst, override val type: Type = expr.constType) : TypedExpr {
    override fun getCallSignatures(): List<CallSignature> = listOf()

    override val sourceExpr: Expr get() = expr
    override fun toSource(cw: CodeWriter): CodeWriter = cw.print("${expr.constValue} (${type})")

    override fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr? {
        return ConstTypedExpr(expr, unify(type, targetType, sourceEnv, targetEnv, bindingEnv)?:throw RuntimeException("unify"))
    }

    override fun toString(): String = toSource(CodeWriter()).toString()
}
class RefTypedExpr(val expr: ExprRef, override val type: Type) : TypedExpr {
    override fun getCallSignatures(): List<CallSignature> = listOf()

    override val sourceExpr: Expr get() = expr
    override fun toSource(cw: CodeWriter): CodeWriter = cw.print("${expr.name} (${type})")

    override fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr? {
        return RefTypedExpr(expr, unify(type, targetType, sourceEnv, targetEnv, bindingEnv)?:throw RuntimeException("unify"))
    }

    override fun toString(): String = toSource(CodeWriter()).toString()
}

class UnresolvedAccessTypedExpr(override val sourceExpr: ExprAccess, val source: TypedExpr, val memberName: String) : TypedExpr {
    override fun getCallSignatures(): List<CallSignature> = listOf()

    override val type: Type = WILD

    override fun toSource(cw: CodeWriter): CodeWriter {
        cw.print("unresolved access")
        cw.indent()
        cw.print("source")
        source.toSource(cw)
        cw.print("member ${memberName}")
        cw.undent()
        return cw
    }

    override fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr? {
        val unifiedSource = source.unify()?:throw RuntimeException("Unable to unify receiver $source in expression $sourceExpr")
        val unifiedSourceType = unifiedSource.type
        if (unifiedSourceType !is TypeClass) {
            throw RuntimeException("Receiver $unifiedSource in expression $sourceExpr is not a class")
        }
        val members = unifiedSourceType.findMembers(memberName)
        if (members.isEmpty()) {
            throw RuntimeException("No such member named '${memberName}' in type '${unifiedSourceType.name}'")
        }
        return ResolvedAccessTypedExpr(sourceExpr, unifiedSource, memberName, members)
    }
}

class ResolvedAccessTypedExpr(override val sourceExpr: ExprAccess, val source: TypedExpr, val memberName: String, val members: List<TypeClassMember>) : TypedExpr {
    override fun getCallSignatures(): List<CallSignature> =
        members.map { member ->
            val memberType = member.type
            if (memberType is TypeFun)
                CallSignature(memberType, { sourceExpr, receiver, args ->
                    UnresolvedDispatchTypedExpr(sourceExpr, receiver, args)
                })
            else
                null
        }.filterNotNull()

    override fun toSource(cw: CodeWriter): CodeWriter {
        cw.print("resolved access")
        cw.indent()
        cw.print("source")
        source.toSource(cw)
        cw.print(".${memberName}")
        cw.undent()
        return cw
    }

    override fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val type: Type = if (members.size > 1) WILD else members[0].type

}

class ResolvedDispatchTypedExpr(override val sourceExpr: ExprDispatch, val receiver: TypedExpr, val unifiedReturnType: Type, val args: List<TypedExpr>) : TypedExpr {
    override fun getCallSignatures(): List<CallSignature> {
        TODO("not implemented")
    }

    override val type = unifiedReturnType
    override fun toSource(cw: CodeWriter): CodeWriter {
        cw.print("resolved dispatch")
        cw.indent()
        cw.print("Type: $type")
        cw.print("Receiver:")
        cw.indent()
        receiver.toSource(cw)
        cw.undent()
        cw.print("Args:")
        cw.indent()
        args.forEach { arg -> arg.toSource(cw) }
        cw.undent()
        cw.undent()
        return cw
    }
    override fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr? {
        TODO("not implemented")
    }


}


class UnresolvedDispatchTypedExpr(override val sourceExpr: ExprDispatch, val receiver: TypedExpr, val args: List<TypedExpr>) : TypedExpr {
    override fun getCallSignatures(): List<CallSignature> = listOf()

    override val type = WILD
    override fun toSource(cw: CodeWriter): CodeWriter {
        cw.print("unresolved dispatch")
        cw.indent()
        cw.print("Type: $type")
        cw.print("Receiver:")
        cw.indent()
        receiver.toSource(cw)
        cw.undent()
        cw.print("Args:")
        cw.indent()
        args.forEach { arg -> arg.toSource(cw) }
        cw.undent()
        cw.undent()
        return cw
    }

    override fun unify(targetType: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): TypedExpr? {
        val unifiedReceiver = receiver.unify() ?: return null
        unifiedReceiver.getCallSignatures()
        val unifiedReceiverSignature = unifiedReceiver.type as TypeFun
        val funBindingEnv = ParameterizedTypeEnv(unifiedReceiverSignature.typeParams.toTypedArray(), bindingEnv, EmptyTypeEnv())
        val targetFunEnv = targetEnv.linkTo(unifiedReceiverSignature.env)

        val unifiedParamExprs = mutableListOf<TypedExpr>()
        for (i in args.indices) {
            val targetArgType = unifiedReceiverSignature.typeParams[i]
            val unifiedParamExpr = args[i].unify(targetArgType, sourceEnv, targetFunEnv, funBindingEnv)?:throw RuntimeException("unification")
            unifiedParamExprs.add(unifiedParamExpr)
        }
        val unifiedReturnType = unify(unifiedReceiverSignature.returnType, targetType, sourceEnv, targetFunEnv, funBindingEnv)
        if (unifiedReturnType == null) {
            return null
        }
        return ResolvedDispatchTypedExpr(sourceExpr, unifiedReceiver, unifiedReturnType, unifiedParamExprs)
    }
}

fun toTypedExpr(expr: Expr): TypedExpr {
    val typedExprs = toTypedExprs(expr)
    if (typedExprs.isEmpty()) {
        throw RuntimeException("No options")
    }
    if (typedExprs.size > 1) {
        throw RuntimeException("Too many options")
    }
    return typedExprs.get(0)
}
fun toTypedExprs(expr: Expr): List<TypedExpr> {
    when (expr) {
        is ExprConst -> return listOf(ConstTypedExpr(expr))
        is ExprDispatch -> return toTypedExprs(expr.receiver)
                .map { receiver -> UnresolvedDispatchTypedExpr(expr, receiver, expr.args.map { toTypedExpr(it) }) }
        is ExprAccess ->
            return toTypedExprs(expr.source)
                    .map { source -> UnresolvedAccessTypedExpr(expr, source, expr.name) }
        is ExprRef -> {
            val exprTypes = SCHEMA.get(expr.name)
            return exprTypes.map { exprType -> RefTypedExpr(expr, exprType) }
        }
    }
    throw RuntimeException("Unhandled expr " + expr)
}

fun printOptions(expr: Expr) {
    println("Processing expression:\n\t$expr")
    val preUnification = toTypedExpr(expr)
    println("Converted to typed expressions:\n\t${preUnification.toSource(CodeWriter())}")
    val postUnification = preUnification.unify(preUnification.type, EmptyTypeEnv(), EmptyTypeEnv(), EmptyTypeEnv())?:throw RuntimeException("Failed to unify")
    println("Unified typed expressions:\n\t${postUnification.toSource(CodeWriter())}")

}

fun main(args: Array<String>) {
    printOptions(xdispatch(xget(xdispatch(xref("listOf"), xconst(INT, "0")), "get"), xconst(INT, "1")))
//    printOptions(xdispatch(xref("listOf"), xconst(INT, "0")))
//    printOptions(xdispatch(xref("echo"), xconst(INT, "0")))
//    printOptions(
//            xref("echo")
//    )
}

