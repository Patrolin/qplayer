package com.patrolin.qplayer.lib

class RingBuffer<T>(val maxSize: Int) {
    val data = arrayListOf<T>()
    var startIndex: Int = 0
    var size = 0
    fun add(value: T) {
        if (size < maxSize) {
            data.add(value)
            size++
        } else {
            data[(startIndex++) % maxSize] = value
        }
    }
    operator fun get(index: Int): T {
        return data[(startIndex + index) % maxSize]
    }
    fun lastOrNull(): T? = if (size > 0) this[size] else null
}