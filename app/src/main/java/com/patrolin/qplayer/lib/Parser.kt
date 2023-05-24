package com.patrolin.qplayer.lib

import java.io.Closeable
import java.io.File

abstract class Flags(private val value: Int) {
    operator fun get(index: Int) = value.ushr(index).and(1) == 1
    abstract fun entries(): Map<String, Boolean>
    override fun toString(): String {
        return "${this.entries().filterValues { it }.keys}"
    }
}

class Parser(file: File): Closeable {
    private val _reader = file.inputStream()
    private fun readOrThrow(): Int {
        val uByte = _reader.read()
        if (uByte == -1) throw Exception("Reached EOF")
        return uByte
    }
    override fun close() = _reader.close()
    // public
    fun readBytes(size: Int): List<Int> {
        return buildList(size) {
            for (i in 0.until(size))
                add(readOrThrow())
        }
    }
    fun readString(size: Int): String {
        return buildString {
            for (i in 0.until(size)) {
                append(Char(readOrThrow())) }
        }
    }
    fun readMagic(expectedMagic: String): Boolean {
        val n = expectedMagic.length
        _reader.mark(n)
        val magic = readString(n)
        val isMagicValid = (magic == expectedMagic)
        if (!isMagicValid) _reader.reset()
        return isMagicValid
    }
    fun readByte(): Int {
        return readOrThrow()
    }
    fun readWordLE(): Int {
        return readOrThrow() + readOrThrow().shl(8)
    }
    fun readDoubleWordLE(): Int {
        return readOrThrow() + readOrThrow().shl(8) + readOrThrow().shl(16) + readOrThrow().shl(24)
    }
    fun readDoubleWordBE(): Int {
        return readOrThrow().shl(24) + readOrThrow().shl(16) + readOrThrow().shl(8) + readOrThrow()
    }
    fun readDoubleWord7BitBE(): Int {
        return readOrThrow().and(0x7f).shl(21) + readOrThrow().and(0x7f).shl(14) + readOrThrow().and(0x7f).shl(7) + readOrThrow().and(0x7f)
    }
    fun skip(bytes: Int) {
        _reader.skip(bytes.toLong())
    }
}

// ID3v2
class ID3v2Flags(val value: Int) : Flags(value) {
    val unSynchronisation get() = this[0]
    val extendedHeader get() = this[1]
    val experimental get() = this[2]
    val footerPresent get() = this[3]

    override fun entries(): Map<String, Boolean> {
        return mapOf(
            "unSynchronisation" to this.unSynchronisation,
            "extendedHeader" to this.extendedHeader,
            "experimental" to this.experimental,
            "footerPresent" to this.footerPresent,
        )
    }
}
class ID3v2FrameFlags(val value: Int) : Flags(value) {
    // status
    val discardOnTagAlter get() = this[1]
    val discardOnFileAlter get() = this[2]
    val readOnly get() = this[3]
    // format
    val grouped get() = this[9]
    val compression get() = this[12]
    val encryption get() = this[13]
    val unSynchronization get() = this[14]
    val dataLengthPresent get() = this[15]

    override fun entries(): Map<String, Boolean> {
        return mapOf(
            "discardOnTagAlter" to this.discardOnTagAlter,
            "discardOnFileAlter" to this.discardOnFileAlter,
            "readOnly" to this.readOnly,
            "grouped" to this.grouped,
            "compression" to this.compression,
            "encryption" to this.encryption,
            "unSynchronization" to this.unSynchronization,
            "dataLengthPresent" to this.dataLengthPresent,
        )
    }
}
fun parseID3v2(file: File): String {
    var artist = "---"
    Parser(file).use {
        var pos = 0
        try {
            if (it.readMagic("ID3")) {
                val version = it.readWordLE()
                if (!listOf(3, 4).contains(version)) return@use
                val id3Flags = ID3v2Flags(it.readByte())
                val id3Size = it.readDoubleWord7BitBE()
                pos += 10
                //errPrint("version: $version, id3Flags: $id3Flags, id3Size: $id3Size")
                if (id3Flags.unSynchronisation) return@use
                val frames = hashMapOf<String, String>()
                val userData = hashMapOf<String, String>()
                fun readFrame() {
                    //errPrint("readFrame() at $pos / $id3Size")
                    val id = it.readString(4)
                    val frameSize =
                        if (version == 4) it.readDoubleWord7BitBE() else it.readDoubleWordBE()
                    val flags = ID3v2FrameFlags(it.readWordLE())
                    val textEncoding = it.readByte()
                    val data = it.readString(frameSize - 1).removeSuffix("\u0000")
                    if (id == "TXXX") {
                        val splitIndex = data.indexOf(Char(0))
                        if (splitIndex != -1)
                            userData[data.slice(0 until splitIndex)] =
                                data.slice(splitIndex + 1 until data.length)
                    } else {
                        frames[id] = data
                    }
                    pos += 10 + frameSize
                    //errPrint("id: $id, frameSize: $frameSize, flags: $flags, data: $data")
                }
                while (pos < id3Size) {
                    readFrame()
                }
                //errPrint("frames: $frames")
                //errPrint("userData: $userData")
                //name = frames.getOrDefault("TIT2", file.name)
                artist = frames.getOrDefault("TPE1", artist)
                //year = frames.getOrDefault("TDRC", "")
                //album = frames.getOrDefault("ALB", "")
                //albumPosition = frames.getOrDefault("TRCK", "")
                //lengthMs = frames.getOrDefault("TLEN", "")
                //mood = frames.getOrDefault("TMOO", "")
                //url = userData.getOrDefault("comment", "")
            }
        } catch (_: Exception) {}
    }
    return artist
}