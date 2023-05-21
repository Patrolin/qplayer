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

@Composable
fun App() {
    val mediaPlayer = MediaPlayer()
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
    var needSongs = true
    var songs = listOf<Song>()
    var outerCurrentlyPlayingIndex = -1
    val currentlyPlaying = remember { mutableStateOf<Song?>(null) }
    fun playSong(song: Song) {
        errPrint("Playing: $song")
        currentlyPlaying.value = song
        mediaPlayer.reset()
        mediaPlayer.setDataSource(song.path)
        mediaPlayer.setVolume(1f, 1f)
        mediaPlayer.prepare()
        mediaPlayer.start()
    }
    fun stopSong() {
        mediaPlayer.setVolume(0f, 0f)
        mediaPlayer.stop()
        currentlyPlaying.value = null
    }
    mediaPlayer.setOnCompletionListener {
        val nextIndex = outerCurrentlyPlayingIndex + 1
        if (nextIndex < songs.size) {
            playSong(songs[nextIndex])
        }
    }
    // TODO: listen to permission change
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
    Column() {
        useTabs(0, listOf("Songs", "Playlists"), rightBlock=rightBlock) { selectedTab ->
            if (selectedTab != 0) needSongs = true
            when (selectedTab) {
                0 -> {
                    if (needSongs) {
                        songs = getSongs()
                        needSongs = false
                    }
                    if (!requestPermissions(*READ_PERMISSIONS)) {
                        Text(getPermissionsText("read"))
                    } else if (songs.isEmpty()) {
                        Text("No songs yet, try adding a Youtube playlist or adding songs to your Music folder!")
                    } else {
                        val currentlyPlayingIndex = songs.indexOfFirst { it == currentlyPlaying.value }
                        outerCurrentlyPlayingIndex = currentlyPlayingIndex
                        LazyColumn(Modifier.weight(1f)) {
                            items(
                                count = songs.size,
                                key = { it },
                                itemContent = {
                                    val song = songs[it]
                                    val isCurrentlyPlaying = (it == currentlyPlayingIndex)
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
        Row(Modifier.padding(4.dp, 8.dp, 4.dp, 4.dp)) {
            Text(currentlyPlaying.value?.name.orEmpty())
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