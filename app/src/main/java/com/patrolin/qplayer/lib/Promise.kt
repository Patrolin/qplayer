package com.patrolin.qplayer.lib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun launchConcurrent(block: suspend CoroutineScope.() -> Unit) {
    CoroutineScope(Dispatchers.Main).launch {
        block()
    }
}
fun getAvailableProcessors() = Runtime.getRuntime().availableProcessors()
class MultithreadedScope {
    val threadPool: ExecutorService = Executors.newWorkStealingPool()
    fun joinThreads() {
        threadPool.shutdown()
        threadPool.awaitTermination(2, TimeUnit.HOURS)
    }
}
fun launchMultithreaded(block: MultithreadedScope.() -> Unit) {
    val multithreadedScope = MultithreadedScope()
    block(multithreadedScope)
}

data class PromiseScope<T>(val resolve: (newResult: T) -> Unit, val reject: () -> Unit)
enum class PromiseState { LOADING, LOADED, ERROR }
typealias PromiseCallback<T> = (promise: Promise<T>) -> Unit
class Promise<T> {
    var state = PromiseState.LOADING
    var value: T? = null
    private var queue = ArrayDeque<PromiseCallback<T>>()
    constructor(create: PromiseScope<T>.() -> Unit) {
        launchConcurrent {
            create(PromiseScope({ newValue ->
                state = PromiseState.LOADED
                value = newValue
                this@Promise.handleCallbacks()
            }, {
                state = PromiseState.ERROR
                this@Promise.handleCallbacks()
            }))
        }
    }
    constructor(_state: PromiseState, _value: T?) {
        this.state = _state
        this.value = _value
    }
    fun then(callback: PromiseCallback<T>) {
        queue.add(callback)
        handleCallbacks()
    }
    private fun handleCallbacks() {
        val copy = Promise(this.state, this.value)
        if (state != PromiseState.LOADING) {
            var f: PromiseCallback<T>
            while (queue.isNotEmpty()) {
                f = queue.removeFirst()
                f.invoke(copy)
            }
        }
    }
    override fun toString(): String {
        return "Promise($state, $value)"
    }
}