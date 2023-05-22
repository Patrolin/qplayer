package com.patrolin.qplayer.components

import android.os.Build
import android.os.Environment
import com.patrolin.qplayer.appContext
import java.io.BufferedInputStream
import java.io.File

fun _getAndroidVersion() = Build.VERSION.RELEASE.replace("(\\d+[.]\\d+)(.*)","$1").toDouble()
fun getAndroidAPIVersion() = Build.VERSION.SDK_INT
fun getAppName(): String {
    val appInfo = appContext.applicationInfo
    val stringId = appInfo.labelRes
    return if (stringId == 0) appInfo.nonLocalizedLabel.toString() else appContext.getString(stringId)
}

fun getMusicFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
fun getVideoFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

data class Song(val path: String, val name: String, val artist: String)
fun getSongsAsync(): Promise<List<Song>> {
    return Promise {
        errPrint("Getting songs...")
        val buffer = ByteArray(128)
        var i = 0
        val songs = getMusicFolder().walk().filter { it.isFile }.map { fd ->
            val bufferedReader = BufferedInputStream(File(fd.absolutePath).inputStream(), 128)
            bufferedReader.read(buffer)
            bufferedReader.close()
            if (i++ < 3) {
                errPrint(buffer.map { it }.joinToString(" ") { it.toUByte().toString() })
            }
            Song(fd.absolutePath, fd.name, "---")
        }.toList()
        errPrint("Found ${songs.size} songs!")
        resolve(songs.sortedBy { it.name })
    }
}