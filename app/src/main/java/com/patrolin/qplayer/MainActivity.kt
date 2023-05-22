package com.patrolin.qplayer

import android.media.MediaPlayer
import android.media.VolumeShaper
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrolin.qplayer.components.DialogState
import com.patrolin.qplayer.components.Icons
import com.patrolin.qplayer.components.READ_PERMISSIONS
import com.patrolin.qplayer.components.SUBTITLE_COLOR
import com.patrolin.qplayer.components.Song
import com.patrolin.qplayer.components.SubTitle
import com.patrolin.qplayer.components.TITLE_COLOR
import com.patrolin.qplayer.components.Text
import com.patrolin.qplayer.components.TextColor
import com.patrolin.qplayer.components.Title
import com.patrolin.qplayer.components.errPrint
import com.patrolin.qplayer.components.getPermissionsText
import com.patrolin.qplayer.components.getSongs
import com.patrolin.qplayer.components.onPermissionChange
import com.patrolin.qplayer.components.openURL
import com.patrolin.qplayer.components.requestPermissions
import com.patrolin.qplayer.components.showToast
import com.patrolin.qplayer.components.useDialog
import com.patrolin.qplayer.components.useTabs
import com.patrolin.qplayer.ui.theme.QPlayerTheme

lateinit var appContext: MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = this
        setContent {
            QPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissions.isNotEmpty())
            onPermissionChange(requestCode)
    }
}

data class GlobalContext(
    var mediaPlayer: MediaPlayer,
    var prevSelectedTab: Int,
) {
    private fun getAudioShaper(duration: Long, shape: FloatArray): VolumeShaper {
        return mediaPlayer.createVolumeShaper(
            VolumeShaper.Configuration.Builder()
                .setDuration(duration)
                .setCurve(floatArrayOf(0f, 1f), shape)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                .build()
        )
    }
    val audioFadeIn: VolumeShaper get() = getAudioShaper(FADE_IN_TIME, floatArrayOf(0f, 1f))
    val audioFadeOut: VolumeShaper get() = getAudioShaper(FADE_OUT_TIME, floatArrayOf(1f, 0f))
    companion object {
        const val FADE_IN_TIME = 1L
        const val FADE_OUT_TIME = 1L
    }
}
val globalContext = GlobalContext(MediaPlayer(), -1)
data class AppState(val songs: List<Song>, val playing: Song?, val nonce: Int) {
    val playingIndex: Int get() = songs.indexOfFirst { it == playing }
    fun withSongs(newSongs: List<Song>): AppState = AppState(newSongs, playing, nonce)
    fun withPlaying(newPlaying: Song?): AppState {
        return AppState(songs, newPlaying, nonce)
    }
    fun incrementNonce(): AppState {
        return AppState(songs, playing, nonce + 1)
    }
}

@Composable
fun App() {
    val aboutDialog = useDialog() {
        Column(Modifier.padding(4.dp)) {
            Title("About", Modifier.padding(4.dp, 0.dp, 0.dp, 0.dp))
            AboutRow("Qplayer (Unlicense)", "https://github.com/Patrolin/qplayer")
            AboutRow("yt-dlp (Unlicense)", "https://github.com/yt-dlp/yt-dlp")
        }
    }
    val rightBlock: @Composable RowScope.() -> Unit = {
        Icons.AboutIcon(SUBTITLE_COLOR, Modifier
            .clickable {
                aboutDialog.value = DialogState(true)
            }
            .padding(8.dp, 4.dp)
        )
    }
    val appState = remember(key1 = "appState") { mutableStateOf(AppState(listOf(), null, 0)) }
    errPrint("appState: ${appState.value.songs.size}, ${appState.value.playing}, ${appState.value.nonce}")
    fun playSong(song: Song) {
        errPrint("Playing: $song")
        globalContext.mediaPlayer.reset()
        globalContext.mediaPlayer.setDataSource(song.path)
        globalContext.audioFadeIn.apply(VolumeShaper.Operation.PLAY)
        globalContext.mediaPlayer.prepare()
        globalContext.mediaPlayer.start()
        appState.value = appState.value.withPlaying(song)
    }
    fun pauseSong() {
        if (globalContext.mediaPlayer.isPlaying) {
            errPrint("Pausing song")
            globalContext.audioFadeOut.apply(VolumeShaper.Operation.PLAY)
            Thread.sleep(GlobalContext.FADE_OUT_TIME)
            globalContext.mediaPlayer.pause()
        } else {
            errPrint("Resuming song")
            globalContext.audioFadeIn.apply(VolumeShaper.Operation.PLAY)
            globalContext.mediaPlayer.start()
        }
    }
    fun stopSong() {
        errPrint("Stopping song")
        globalContext.audioFadeOut.apply(VolumeShaper.Operation.PLAY)
        Thread.sleep(GlobalContext.FADE_OUT_TIME)
        globalContext.mediaPlayer.stop()
        appState.value = appState.value.withPlaying(null)
    }
    globalContext.mediaPlayer.setOnCompletionListener {
        errPrint("Song completed: ${appState.value.songs.size}, ${appState.value.playing}, ${appState.value.nonce}")
        val nextIndex = appState.value.playingIndex + 1
        if (nextIndex < appState.value.songs.size) {
            playSong(appState.value.songs[nextIndex])
        } else {
            appState.value = appState.value.withPlaying(null)
        }
    }
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
    Column() {
        useTabs(0, listOf("Songs", "Playlists"), rightBlock=rightBlock) { selectedTab ->
            val haveReadPermissions = requestPermissions(*READ_PERMISSIONS) {
                errPrint("Permission change!")
                globalContext.prevSelectedTab = -1
                appState.value = appState.value.incrementNonce()
            }
            if (selectedTab != globalContext.prevSelectedTab) {
                // TODO: fix weird bug
                errPrint("Tab changed, $selectedTab, ${globalContext.prevSelectedTab}")
                appState.value = appState.value.withSongs(getSongs())
            }
            globalContext.prevSelectedTab = selectedTab
            when (selectedTab) {
                0 -> {
                    // TODO: async get songs
                    if (!haveReadPermissions) {
                        Text(getPermissionsText("read"), Modifier.weight(1f))
                    } else if (appState.value.songs.isEmpty()) {
                        Text("No songs yet, try adding a Youtube playlist or adding songs to your Music folder!", Modifier.weight(1f))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(
                                count = appState.value.songs.size,
                                key = { it },
                                itemContent = {
                                    val song = appState.value.songs[it]
                                    val isCurrentlyPlaying = (it == appState.value.playingIndex)
                                    SongRow(song.name, song.artist, isCurrentlyPlaying) {
                                        if (isCurrentlyPlaying) {
                                            stopSong()
                                        } else {
                                            try {
                                                playSong(song)
                                            } catch (error: Exception) {
                                                errPrint("$error")
                                                showToast("$error", Toast.LENGTH_LONG)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                1 -> {
                    Row(Modifier.weight(1f)) {
                        Text("TODO: playlists")
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp)) {
            Title(appState.value.playing?.name.orEmpty(), modifier = Modifier.padding(0.dp, 0.dp, 4.dp, 0.dp).weight(1f), wrap = false)
            if (globalContext.mediaPlayer.isPlaying)
                Icons.PauseIcon(color = TITLE_COLOR, modifier = Modifier.clickable { pauseSong() })
            else
                Icons.PlayIcon(color = TITLE_COLOR, modifier = Modifier.clickable { pauseSong() })
            // TODO: shuffle toggle
        }
    }
}

@Composable
fun AboutRow(title: String, url: String) {
    Row(
        Modifier
            .clickable { openURL(url) }
            .width(200.dp)
            .padding(0.dp, 2.dp)) {
        Icons.GithubIcon(color = TITLE_COLOR,
            Modifier
                .padding(4.dp)
                .align(Alignment.CenterVertically)
        )
        SubTitle(title, Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
fun SongRow(name: String, author: String, isCurrentlyPlaying: Boolean, onClick: () -> Unit = {}) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .height(54.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(8.dp, 4.dp, 32.dp, 4.dp)) {
            // TODO: fadeout on right side
            Title(name.trim(), color = if(isCurrentlyPlaying) TextColor.PRIMARY else TextColor.DEFAULT, wrap = false)
            SubTitle(author, color = if(isCurrentlyPlaying) TextColor.PRIMARY else TextColor.DEFAULT, wrap = false)
        }
    }
}