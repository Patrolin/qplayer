package com.patrolin.qplayer.lib

import android.os.Build
import android.os.Environment
import android.util.Log
import com.patrolin.qplayer.appContext
import java.io.File

// common
fun _getAndroidVersion() = Build.VERSION.RELEASE.replace("(\\d+[.]\\d+)(.*)","$1").toDouble()
fun getAndroidAPIVersion() = Build.VERSION.SDK_INT
fun getAppName(): String {
    val appInfo = appContext.applicationInfo
    val stringId = appInfo.labelRes
    return if (stringId == 0) appInfo.nonLocalizedLabel.toString() else appContext.getString(stringId)
}

fun errPrint(message: String) {
    Log.d("AYAYA", message)
}

// data
fun getMusicFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
fun getVideoFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

data class Song(val path: String, val name: String, val artist: String)
fun getSongsAsync(): Promise<List<Song>> {
    return Promise {
        errPrint("Getting songs...")
        var i = 0
        val songs = getMusicFolder().walk().filter { it.isFile }.map { file ->
            errPrint("Parsing #${i++}")
            val artist = parseID3v2(file)
            Song(file.absolutePath, file.name, artist)
        }.toList()
        errPrint("Found ${songs.size} songs!")
        resolve(songs.sortedBy { it.name })
    }
}