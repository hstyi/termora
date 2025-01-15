package app.termora.native.osx

import app.termora.ApplicationScope
import java.lang.reflect.Method

class DispatchNative private constructor() {
    companion object {
        fun getInstance(): DispatchNative {
            return ApplicationScope.forApplicationScope().getOrCreate(DispatchNative::class) { DispatchNative() }
        }
    }

    val dispatch_main_queue: Long

    private val nativeExecuteAsync: Method

    init {
        val clazz = Class.forName("sun.lwawt.macosx.concurrent.LibDispatchNative")

        val nativeGetMainQueue = clazz.getDeclaredMethod("nativeGetMainQueue")
        nativeGetMainQueue.isAccessible = true
        dispatch_main_queue = nativeGetMainQueue.invoke(null) as Long

        nativeExecuteAsync = clazz.getDeclaredMethod(
            "nativeExecuteAsync",
            *arrayOf(Long::class.java, Runnable::class.java)
        )
        nativeExecuteAsync.isAccessible = true

    }


    fun dispatch_async(runnable: Runnable) {
        nativeExecuteAsync.invoke(null, dispatch_main_queue, runnable)
    }
}