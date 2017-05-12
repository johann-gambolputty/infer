fun unify(source: Type, target: Type): Type? = unify(source, target, EmptyTypeEnv(), EmptyTypeEnv(), EmptyTypeEnv())

fun unify(source: Type, target: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): Type? {
    if (source is TypeClass) {
        return unifyClassTo(source, target, sourceEnv, targetEnv, bindingEnv)
    }
    if (source is TypeVar) {
        return unifyTypeVarTo(source, target, sourceEnv, targetEnv, bindingEnv)
    }
    if (source is TypeFun) {
        return unifyFunTo(source, target, sourceEnv, targetEnv, bindingEnv)
    }
    return null
}

fun unifyFunTo(source: TypeFun, target: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): Type? {
    if (target is TypeFun) {
        return unifyFunToFun(source, target, sourceEnv, targetEnv, bindingEnv)
    }
    if (target is TypeWild) {
        return source // TODO
    }
    return null
}

fun unifyFunToFun(source: TypeFun, target: TypeFun, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): Type? {
    if (source.params.size != target.params.size) {
        return null
    }
    val funBindingEnv = ParameterizedTypeEnv((source.typeParams + target.typeParams).toTypedArray(), bindingEnv, EmptyTypeEnv())
    val sourceFunEnv = sourceEnv.linkTo(source.env)
    val targetFunEnv = targetEnv.linkTo(target.env)

    val unifiedParamTypes = Array<Type>(target.params.size, { UNIT })
    for (i in target.params.indices) {
        val sourceParamType = source.params[i]
        val targetParamType = target.params[i]
        val unifiedParamType = unify(sourceParamType, targetParamType, sourceFunEnv, targetFunEnv, funBindingEnv) ?: return null
        unifiedParamTypes[i] = unifiedParamType
    }
    val unifiedReturnType = unify(source.returnType, target.returnType, sourceFunEnv, targetFunEnv, funBindingEnv) ?: return null
    val result = TypeFun(target.name, funBindingEnv)
    result.returnType = unifiedReturnType
    result.params.addAll(unifiedParamTypes)
    return result
}

fun unifyTypeVarTo(source: TypeVar, target: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): Type? {
    val resolvedSource = sourceEnv.resolve(source) ?: bindingEnv.resolve(source)
    if (resolvedSource != null) {
        bindingEnv.bind(source, resolvedSource)
        if (source == target) {
            //return resolvedSource // NOTE stops infinite recursion but does not allow chained bindings
        }
        return unify(resolvedSource, target, sourceEnv, targetEnv, bindingEnv)
    }

    if (target is TypeVar) {
        val resolvedTarget = targetEnv.resolve(target) ?: bindingEnv.resolve(target)
        if (resolvedTarget != null) {
            bindingEnv.bind(target, resolvedTarget)
            return unifyTypeVarTo(source, resolvedTarget, sourceEnv, targetEnv, bindingEnv)
        }
    }
    if (source == target) {
        return target
    }
    bindingEnv.bind(source, target)
    return target
}

fun unifyClassTo(source: TypeClass, target: Type, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): Type? {
    if (target is TypeClass) {
        return unifyClassToClass(source, target, sourceEnv, targetEnv, bindingEnv)
    }
    if (target is TypeVar) {
        return unifyTypeVarTo(target, source, targetEnv, sourceEnv, bindingEnv)
    }
    if (target is TypeWild) {
        return source // TODO
    }
    return null
}

fun unifyClassToClass(source: TypeClass, target: TypeClass, sourceEnv: TypeEnv, targetEnv: TypeEnv, bindingEnv: BindingTypeEnv): Type? {
    if (source.typeParams.size != target.typeParams.size) {
        return null
    }
    val classBindingEnv = target.createEnv(bindingEnv)
    val sourceClassEnv = sourceEnv.linkTo(source.env)
    val targetClassEnv = targetEnv.linkTo(target.env)
    val unifiedClassEnv = target.createEnv()
    for (i in target.typeParams.indices) {
        val sourceTypeParam = source.typeParams[i]
        val targetTypeParam = target.typeParams[i]
        val unifiedTypeParam = unify(sourceTypeParam, targetTypeParam, sourceClassEnv, targetClassEnv, classBindingEnv) ?: return null
        unifiedClassEnv.bind(targetTypeParam, unifiedTypeParam)
    }
    return target.withEnv(unifiedClassEnv)
}
