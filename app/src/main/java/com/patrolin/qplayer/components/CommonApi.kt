package com.patrolin.qplayer.components

import android.os.Build
import android.os.Environment
import com.patrolin.qplayer.appContext
import java.io.BufferedInputStream

fun getAndroidVersion() = Build.VERSION.RELEASE.replace("(\\d+[.]\\d+)(.*)","$1").toDouble()
fun getAppName(): String {
    val appInfo = appContext.applicationInfo
    val stringId = appInfo.labelRes
    return if (stringId == 0) appInfo.nonLocalizedLabel.toString() else appContext.getString(stringId)
}

fun getMusicFolder() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
fun getVideoFolder() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

data class Song(val path: String, val name: String, val artist: String)
fun getSongs(): List<Song> {
    errPrint("Getting songs...")
    val buffer = ByteArray(128)
    val songs = getMusicFolder().walk().filter { it.isFile }.map { fd ->
        val bufferedReader = BufferedInputStream(fd.inputStream(), 128)
        bufferedReader.read(buffer)
        bufferedReader.close()
        errPrint(buffer.map { it }.joinToString(" ") { it.toString() })
        Song(fd.absolutePath, fd.name, "---")
    }.toList()
    return songs.sortedBy { it.name }
}