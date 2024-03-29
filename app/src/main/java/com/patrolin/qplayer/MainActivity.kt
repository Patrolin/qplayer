package com.patrolin.qplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.patrolin.qplayer.components.DIVIDER_COLOR
import com.patrolin.qplayer.components.Icons
import com.patrolin.qplayer.components.SUBTITLE_COLOR
import com.patrolin.qplayer.components.SongsTab
import com.patrolin.qplayer.components.SubTitle
import com.patrolin.qplayer.components.TITLE_COLOR
import com.patrolin.qplayer.components.Tabs
import com.patrolin.qplayer.components.Text
import com.patrolin.qplayer.components.TextColor
import com.patrolin.qplayer.components.Title
import com.patrolin.qplayer.components.rememberTabsState
import com.patrolin.qplayer.components.useDialog
import com.patrolin.qplayer.lib.*
import com.patrolin.qplayer.ui.theme.QPlayerTheme
import kotlin.math.roundToInt

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
        Column(Modifier.background(Color.Black).padding(4.dp)) {
            Title("About", Modifier.padding(4.dp, 0.dp, 0.dp, 0.dp))
            AboutRow("Qplayer (Unlicense)", "https://github.com/Patrolin/qplayer")
            AboutRow("yt-dlp (Unlicense)", "https://github.com/yt-dlp/yt-dlp")
        }
    }
    val rightBlock: @Composable RowScope.() -> Unit = {
        Icons.AboutIcon(SUBTITLE_COLOR, Modifier
            .clickable {
                aboutDialog.open = true
            }
            .padding(8.dp, 4.dp)
        )
    }
    // TODO: is any of this correct? https://developer.android.com/jetpack/compose/side-effects
    val (nonce, setNonce) = rememberSaveable { mutableStateOf(0, neverEqualPolicy()) }
    fun getState() = GlobalContext._appState
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
    val tabsState = rememberTabsState(1)
    val playingTabScrollState = rememberScrollState(0)
    val songsTabScrollState = rememberScrollState(0)
    val pixelDensity = LocalDensity.current.density
    val switchAndScrollToPlaying = {
        val state = getState()
        launchConcurrent {
            tabsState.selectedIndex = 0
            setNonce(nonce + 1)
            val playingIndex = state.playOrder.indexOf(state.playing)
            if (playingIndex >= 0) {
                playingTabScrollState.scrollTo((playingIndex * 54 * pixelDensity).roundToInt())
            }
        }
    }
    GlobalContext.onCompletionListener = {
        GlobalContext.playNextSong(setState)
        switchAndScrollToPlaying()
    }
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
    // https://developer.android.com/reference/android/media/AudioManager
    Column() {
        Tabs(tabsState, listOf("Playing", "Songs", "Playlists"), rightBlock=rightBlock) { selectedTab ->
            when (selectedTab) {
                0 -> {
                    val state = getState()
                    SongsTab(state, setState, haveReadPermissions, state.playOrder, true, switchAndScrollToPlaying, playingTabScrollState)
                }
                1 -> {
                    val state = getState()
                    SongsTab(state, setState, haveReadPermissions, state.songs, false, switchAndScrollToPlaying, songsTabScrollState)
                }
                2 -> {
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
                            .clickable { GlobalContext.playPauseSong(setState, switchAndScrollToPlaying) }
                            .align(Alignment.CenterVertically))
                    else
                        Icons.PlayIcon(color = TITLE_COLOR, modifier = Modifier
                            .clickable { GlobalContext.playPauseSong(setState, switchAndScrollToPlaying) }
                            .align(Alignment.CenterVertically))
                    Icons.NextIcon(color = TITLE_COLOR, modifier = Modifier.clickable { GlobalContext.playNextSong(setState) }.align(Alignment.CenterVertically))
                }
                Row(Modifier.padding(0.dp, 4.dp, 0.dp, 0.dp)) {
                    Icons.StopIcon(color = TITLE_COLOR, modifier = Modifier.clickable { GlobalContext.stopSong(setState) }.align(Alignment.CenterVertically))
                    val shuffleIconModifier = Modifier
                        .clickable {
                            GlobalContext.toggleShuffleState(setState)
                            if (tabsState.selectedIndex == 0)
                                switchAndScrollToPlaying()
                        }
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
        // TODO: fix this
        SubTitle("${i+1}",
            Modifier
                .align(Alignment.CenterVertically)
                .padding(6.dp, 0.dp, 0.dp, 0.dp), color = rowColor)
        Column(
            Modifier
                .align(Alignment.CenterVertically)
                .fillMaxWidth()
                .padding(6.dp, 4.dp, 32.dp, 4.dp)) {
            // TODO: fadeout on right side?
            Title(name.trim(), color = rowColor, wrap = false)
            SubTitle(author, color = rowColor, wrap = false)
        }
    }
}