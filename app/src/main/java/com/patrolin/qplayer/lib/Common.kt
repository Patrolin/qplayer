package com.patrolin.qplayer.lib

import android.os.Build
import android.os.Environment
import android.util.Log
import com.patrolin.qplayer.appContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        //errPrint("available processors: ${Runtime.getRuntime().availableProcessors()}")
        val threadPool = Executors.newWorkStealingPool()
        errPrint("Getting songs...")
        val accSongs = arrayListOf<Song>()
        for (file in getMusicFolder().walk().filter { it.isFile }) {
            threadPool.submit {
                //errPrint("Parsing #${i++}")
                val artist = parseID3v2(file)
                val song = Song(file.absolutePath, file.name, artist)
                synchronized(accSongs) {
                    accSongs.add(song)
                }
            }
        }
        threadPool.shutdown()
        threadPool.awaitTermination(2, TimeUnit.HOURS)
        errPrint("Found ${accSongs.size} songs!")
        resolve(accSongs.sortedBy { it.name })
    }
}