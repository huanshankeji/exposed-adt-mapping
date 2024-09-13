package com.huanshankeji.exposed.datamapping.classproperty

import kotlin.reflect.KFunction

class ReflectionFunctionInvocationException(constructor: KFunction<*>, vararg args: Any?, cause: Throwable) :
    Exception("calling the function $constructor with params ${args.toList()}", cause)

// also consider catching only in debug/test mode
fun <R> KFunction<R>.callWithCatch(vararg args: Any?) =
    try {
        call(args = args)
    } catch (e: Exception) {
        throw ReflectionFunctionInvocationException(this, args = args, cause = e)
    }
