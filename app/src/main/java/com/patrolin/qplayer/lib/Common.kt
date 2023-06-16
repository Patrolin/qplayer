package com.patrolin.qplayer.lib

import android.graphics.Rect
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
fun getScreenSize() : Rect {
    return appContext.windowManager.currentWindowMetrics.bounds
}

fun errPrint(message: String) {
    Log.d("AYAYA", message)
}
fun getTimeStamp(): Long = System.currentTimeMillis()
class Debounce(private val delay: Int) {
    private var lastCall = System.currentTimeMillis()
    fun call(callback: (() -> Unit)?) {
        val now = System.currentTimeMillis()
        if ((callback != null) && (now >= lastCall + delay)) {
            lastCall = now
            callback()
        }
    }
}
fun debounce(delay: Int, callback: () -> Unit): (() -> Unit) {
    val debounce = Debounce(delay)
    return { debounce.call(callback) }
}

// data
fun getMusicFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
fun getVideoFolder(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

data class Song(val path: String, val name: String, val artist: String)
fun getSongsAsync(): Promise<List<Song>> {
    return Promise {
        launchMultithreaded {
            errPrint("Getting songs...")
            val accSongs = arrayListOf<Song>()
            for (file in getMusicFolder().walk().filter { it.isFile }) {
                threadPool.submit {
                    val artist = parseID3v2(file)
                    val song = Song(file.absolutePath, file.name, artist)
                    synchronized(accSongs) {
                        accSongs.add(song)
                    }
                }
            }
            joinThreads()
            errPrint("Found ${accSongs.size} songs!")
            resolve(accSongs.sortedBy { it.name.uppercase() })
        }
    }
}