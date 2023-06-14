package com.patrolin.qplayer

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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrolin.qplayer.components.DIVIDER_COLOR
import com.patrolin.qplayer.components.DialogState
import com.patrolin.qplayer.components.Icons
import com.patrolin.qplayer.components.SUBTITLE_COLOR
import com.patrolin.qplayer.components.SubTitle
import com.patrolin.qplayer.components.TITLE_COLOR
import com.patrolin.qplayer.components.Text
import com.patrolin.qplayer.components.TextColor
import com.patrolin.qplayer.components.Title
import com.patrolin.qplayer.components.useDialog
import com.patrolin.qplayer.components.useTabs
import com.patrolin.qplayer.lib.*
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

@Composable
fun App() {
    val aboutDialog = useDialog() {
        // TODO: give this a background
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
    val (nonce, setNonce) = rememberSaveable { mutableStateOf(0, neverEqualPolicy()) }
    fun getState() = GlobalContext._appState
    /*fun setState(newState: AppState) {
        synchronized(GlobalContext) {
            GlobalContext._appState = newState
            setNonce(nonce + 1)
        }
    }*/
    val setState: (AppState) -> Unit = { newState ->
        synchronized(GlobalContext) {
            GlobalContext._appState = newState
            setNonce(nonce + 1)
        }
    }
    val haveReadPermissions = requestPermissions(*READ_PERMISSIONS) {
        errPrint("Permission change!")
        setState(getState())
    }
    LaunchedEffect(haveReadPermissions) {
        if (haveReadPermissions) {
            getSongsAsync().then {
                setState(getState().withSongs(it))
            }
        }
    }
    GlobalContext.onCompletionListener = {
        GlobalContext.playNextSong(setState)
    }
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
    // https://developer.android.com/reference/android/media/AudioManager
    Column() {
        useTabs(0, listOf("Songs", "Playlists"), rightBlock=rightBlock) { selectedTab ->
            // TODO: playOrder tab
            when (selectedTab) {
                0 -> {
                    if (!haveReadPermissions) {
                        Text(getPermissionsText("read"), Modifier.weight(1f))
                    } else if (getState().songsLoading) {
                        Text("Loading...", Modifier.weight(1f))
                    } else if (getState().songs.isEmpty()) {
                        Text("No songs yet, try adding a Youtube playlist or adding songs to your Music folder!", Modifier.weight(1f))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(
                                count = getState().songs.size,
                                key = { it },
                                itemContent = {
                                    val state = getState()
                                    val song = state.songs[it]
                                    val isPlaying = (song == state.playing) && (state.playingState == PlayingState.PLAYING)
                                    SongRow(it, song.name, song.artist, isPlaying) {
                                        if (isPlaying) {
                                            GlobalContext.stopSong(setState)
                                        } else {
                                            try {
                                                GlobalContext.startSong(song, setState)
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
        Divider(color = DIVIDER_COLOR)
        Row(Modifier.padding(16.dp, 8.dp, 16.dp, 6.dp)) {
            Column(
                Modifier
                    .padding(0.dp, 0.dp, 4.dp, 0.dp)
                    .weight(1f)
            ) {
                val state = getState()
                Title(state.playing?.name.orEmpty())
                SubTitle(state.playing?.artist.orEmpty())
                Title(if(state.playing != null) "${state.currentPosition / 1000}s / ${GlobalContext.mediaPlayer.duration / 1000}s" else "")
            }
            Column(Modifier.align(Alignment.CenterVertically)) {
                Row {
                    Icons.PrevIcon(color = TITLE_COLOR, modifier = Modifier.align(Alignment.CenterVertically))
                    if (getState().playingState == PlayingState.PLAYING)
                        Icons.PauseIcon(color = TITLE_COLOR, modifier = Modifier
                            .clickable { GlobalContext.playPauseSong(setState) }
                            .align(Alignment.CenterVertically))
                    else
                        Icons.PlayIcon(color = TITLE_COLOR, modifier = Modifier
                            .clickable { GlobalContext.playPauseSong(setState) }
                            .align(Alignment.CenterVertically))
                    Icons.NextIcon(color = TITLE_COLOR, modifier = Modifier.clickable { GlobalContext.playNextSong(setState) }.align(Alignment.CenterVertically))
                }
                Row(Modifier.padding(0.dp, 4.dp, 0.dp, 0.dp)) {
                    Icons.StopIcon(color = TITLE_COLOR, modifier = Modifier.clickable { GlobalContext.stopSong(setState) }.align(Alignment.CenterVertically))
                    val shuffleIconModifier = Modifier
                        .clickable { GlobalContext.toggleShuffleState(setState) }
                        .align(Alignment.CenterVertically)
                    if (getState().shuffle) {
                        Icons.ShuffleIcon(color = TITLE_COLOR, modifier = shuffleIconModifier)
                    } else {
                        Icons.ShuffleOffIcon(color = TITLE_COLOR, modifier = shuffleIconModifier)
                    }
                    val loopIconModifier = Modifier
                        .clickable { GlobalContext.toggleLoopState(setState) }
                        .align(Alignment.CenterVertically)
                    when (getState().loopState) {
                        LoopState.LOOP_ALL -> Icons.LoopAllIcon(color = TITLE_COLOR, modifier = loopIconModifier)
                        LoopState.LOOP_ONE -> Icons.LoopOneIcon(color = TITLE_COLOR, modifier = loopIconModifier)
                        LoopState.PLAY_ALL -> Icons.PlayAllIcon(color = TITLE_COLOR, modifier = loopIconModifier)
                        LoopState.PLAY_ONE -> Icons.PlayOneIcon(color = TITLE_COLOR, modifier = loopIconModifier)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutRow(title: String, url: String) {
    Row(
        Modifier.clickable { openURL(url) }.width(200.dp).padding(0.dp, 2.dp)
    ) {
        Icons.GithubIcon(color = TITLE_COLOR, Modifier.align(Alignment.CenterVertically).padding(4.dp))
        SubTitle(title, Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
fun SongRow(i: Int, name: String, author: String, highlight: Boolean, onClick: () -> Unit = {}) {
    val rowColor = if(highlight) TextColor.PRIMARY else TextColor.DEFAULT
    Row(
        Modifier.clickable(onClick = onClick).height(54.dp)
    ) {
        SubTitle("${i+1}",
            Modifier
                .align(Alignment.CenterVertically)
                .padding(6.dp, 0.dp, 0.dp, 0.dp), color = rowColor)
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.CenterVertically)
                .padding(6.dp, 4.dp, 32.dp, 4.dp)) {
            // TODO: fadeout on right side?
            Title(name.trim(), color = rowColor, wrap = false)
            SubTitle(author, color = rowColor, wrap = false)
        }
    }
}