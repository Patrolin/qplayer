package com.patrolin.qplayer

import android.media.MediaPlayer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrolin.qplayer.components.DialogState
import com.patrolin.qplayer.components.READ_PERMISSIONS
import com.patrolin.qplayer.components.SUBTITLE_COLOR
import com.patrolin.qplayer.components.Song
import com.patrolin.qplayer.components.SubTitle
import com.patrolin.qplayer.components.Text
import com.patrolin.qplayer.components.TextColor
import com.patrolin.qplayer.components.Title
import com.patrolin.qplayer.components.errPrint
import com.patrolin.qplayer.components.getPermissionsText
import com.patrolin.qplayer.components.getSongs
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
}

data class GlobalContext(
    var mediaPlayer: MediaPlayer,
    var prevSelectedTab: Int,
)
val globalContext = GlobalContext(MediaPlayer(), -1)
data class AppState(val songs: List<Song>, val playing: Song?) {
    val playingIndex: Int get() = songs.indexOfFirst { it == playing }
    fun withSongs(newSongs: List<Song>): AppState = AppState(newSongs, playing)
    fun withPlaying(newPlaying: Song?): AppState {
        return AppState(songs, newPlaying)
    }
}

@Composable
fun App() {
    val aboutDialog = useDialog() {
        Column() {
            Title("Qplayer (Unlicense)")
            //Icon(Icons.Filled.Github)
            Title("yt-dlp (Unlicense)")
            //Icon(Icons.Filled.Github)
        }
    }
    val rightBlock: @Composable RowScope.() -> Unit = {
        Icon(Icons.Outlined.Info,
            contentDescription = "About",
            tint = SUBTITLE_COLOR,
            modifier = Modifier
                .clickable {
                    aboutDialog.value = DialogState(true)
                }
                .padding(8.dp, 4.dp)
        )
    }
    val appState = remember { mutableStateOf(AppState(listOf(), null)) }
    var mediaPlayer = MediaPlayer()
    fun stopSong() {
        errPrint("Stopping playback")
        globalContext.mediaPlayer.stop()
        appState.value = appState.value.withPlaying(null)
    }
    fun playSong(song: Song) {
        errPrint("Playing: $song")
        globalContext.mediaPlayer.reset()
        globalContext.mediaPlayer.setDataSource(song.path)
        globalContext.mediaPlayer.prepare()
        globalContext.mediaPlayer.start()
        appState.value = appState.value.withPlaying(song)
    }
    mediaPlayer.setOnCompletionListener {
        val nextIndex = appState.value.playingIndex + 1
        if (nextIndex < appState.value.songs.size) {
            playSong(appState.value.songs[nextIndex])
        } else {
            appState.value = appState.value.withPlaying(null)
        }
    }
    // TODO: listen to permission change
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
    Column() {
        useTabs(0, listOf("Songs", "Playlists"), rightBlock=rightBlock) { selectedTab ->
            if (selectedTab != globalContext.prevSelectedTab)
                appState.value = appState.value.withSongs(getSongs())
            globalContext.prevSelectedTab = selectedTab
            when (selectedTab) {
                0 -> {
                    if (!requestPermissions(*READ_PERMISSIONS)) {
                        Text(getPermissionsText("read"))
                    } else if (appState.value.songs.isEmpty()) {
                        Text("No songs yet, try adding a Youtube playlist or adding songs to your Music folder!")
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
                    Text("TODO: playlists")
                }
            }
        }
        Row(Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp)) {
            Text(appState.value.playing?.name.orEmpty())
        }
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