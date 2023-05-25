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
import com.patrolin.qplayer.lib.Promise
import com.patrolin.qplayer.lib.PromiseState
import com.patrolin.qplayer.lib.READ_PERMISSIONS
import com.patrolin.qplayer.components.SUBTITLE_COLOR
import com.patrolin.qplayer.lib.Song
import com.patrolin.qplayer.components.SubTitle
import com.patrolin.qplayer.components.TITLE_COLOR
import com.patrolin.qplayer.components.Text
import com.patrolin.qplayer.components.TextColor
import com.patrolin.qplayer.components.Title
import com.patrolin.qplayer.lib.getPermissionsText
import com.patrolin.qplayer.lib.getSongsAsync
import com.patrolin.qplayer.lib.onPermissionChange
import com.patrolin.qplayer.lib.openURL
import com.patrolin.qplayer.lib.requestPermissions
import com.patrolin.qplayer.lib.showToast
import com.patrolin.qplayer.components.useDialog
import com.patrolin.qplayer.components.useTabs
import com.patrolin.qplayer.lib.errPrint
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

object GlobalContext {
    // app state (rerender on change)
    var _appState = AppState(true, listOf(), null, PlayingState.STOPPED)
    // global context (don't rerender on change)
    val mediaPlayer: MediaPlayer = MediaPlayer()
    var onCompletionListener: (() -> Unit)? = null
    init {
        mediaPlayer.setOnCompletionListener {
            onCompletionListener?.invoke()
        }
    }
    private fun getAudioShaper(shape: FloatArray): VolumeShaper {
        return mediaPlayer.createVolumeShaper(
            VolumeShaper.Configuration.Builder()
                .setDuration(1L)
                .setCurve(floatArrayOf(0f, 1f), shape)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                .build()
        )
    }
    val audioFadeIn: VolumeShaper get() = getAudioShaper(floatArrayOf(0f, 1f))
    // TODO: audio fade out?
}
enum class PlayingState { STOPPED, PLAYING, PAUSED }
data class AppState(
    val songsLoading: Boolean,
    val songs: List<Song>,
    val current: Song?,
    val playing: PlayingState
) {
    fun withSongs(newSongs: Promise<List<Song>>): AppState = AppState(newSongs.state == PromiseState.LOADING, newSongs.value ?: listOf(), current, playing)
    fun withCurrent(newCurrent: Song?, newPlaying: PlayingState? = null): AppState {
        return AppState(songsLoading, songs, newCurrent, newPlaying ?: playing)
    }
    val currentIndex: Int get() = songs.indexOfFirst { it == current }
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
    val (nonce, setNonce) = rememberSaveable { mutableStateOf(0, neverEqualPolicy()) }
    fun getState() = GlobalContext._appState
    fun setState(newState: AppState) {
        GlobalContext._appState = newState
        setNonce(nonce + 1)
    }
    val haveReadPermissions = requestPermissions(*READ_PERMISSIONS) {
        errPrint("Permission change!")
        setState(getState().copy())
    }
    LaunchedEffect(haveReadPermissions) {
        if (haveReadPermissions) {
            getSongsAsync().then {
                setState(getState().withSongs(it))
            }
        }
    }

    fun startSong(song: Song) {
        errPrint("Playing: $song")
        GlobalContext.mediaPlayer.reset()
        GlobalContext.mediaPlayer.setDataSource(song.path)
        GlobalContext.audioFadeIn.apply(VolumeShaper.Operation.PLAY)
        GlobalContext.mediaPlayer.prepare()
        GlobalContext.mediaPlayer.start()
        setState(getState().withCurrent(song, PlayingState.PLAYING))
    }
    fun playPauseSong() {
        val state = getState()
        when (state.playing) {
            PlayingState.PLAYING -> {
                errPrint("Pausing song")
                GlobalContext.mediaPlayer.pause()
                setState(state.withCurrent(state.current, PlayingState.PAUSED))
            }
            PlayingState.PAUSED -> {
                GlobalContext.audioFadeIn.apply(VolumeShaper.Operation.PLAY)
                GlobalContext.mediaPlayer.start()
                setState(state.withCurrent(state.current, PlayingState.PLAYING))
            }
            PlayingState.STOPPED -> {
                val song = state.current ?: state.songs.getOrNull(0)
                if (song != null) startSong(song)
            }
        }
    }
    fun stopSong() {
        errPrint("Stopping song")
        GlobalContext.mediaPlayer.stop()
        val state = getState()
        setState(state.withCurrent(state.current, PlayingState.STOPPED))
    }
    GlobalContext.onCompletionListener = {
        errPrint("Song completed: ${getState().songs.size}, ${getState().current}")
        val nextIndex = getState().currentIndex + 1
        if (nextIndex < getState().songs.size) {
            startSong(getState().songs[nextIndex])
        } else {
            setState(getState().withCurrent(null, PlayingState.STOPPED))
        }
    }
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
    Column() {
        useTabs(0, listOf("Songs", "Playlists"), rightBlock=rightBlock) { selectedTab ->
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
                                    val isPlaying = (it == state.currentIndex) && (state.playing == PlayingState.PLAYING)
                                    // TODO: display song index
                                    SongRow(it, song.name, song.artist, isPlaying) {
                                        if (isPlaying) {
                                            stopSong()
                                        } else {
                                            try {
                                                startSong(song)
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
                    .weight(1f)) {
                Title(getState().current?.name.orEmpty())
                SubTitle(getState().current?.artist.orEmpty())
            }
            if (getState().playing == PlayingState.PLAYING)
                Icons.PauseIcon(color = TITLE_COLOR, modifier = Modifier.clickable { playPauseSong() }.align(Alignment.CenterVertically))
            else
                Icons.PlayIcon(color = TITLE_COLOR, modifier = Modifier.clickable { playPauseSong() }.align(Alignment.CenterVertically))
            Icons.ShuffleIcon(color = TITLE_COLOR, modifier = Modifier.align(Alignment.CenterVertically)) // TODO: shuffle toggle
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
fun SongRow(i: Int, name: String, author: String, highlight: Boolean, onClick: () -> Unit = {}) {
    val rowColor = if(highlight) TextColor.PRIMARY else TextColor.DEFAULT
    Row(
        Modifier
            .clickable(onClick = onClick)
            .height(54.dp)) {
        SubTitle("${i+1}",
            Modifier
                .padding(4.dp, 0.dp, 0.dp, 0.dp)
                .align(Alignment.CenterVertically), color = rowColor)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(8.dp, 4.dp, 32.dp, 4.dp)) {
            // TODO: fadeout on right side
            Title(name.trim(), color = rowColor, wrap = false)
            SubTitle(author, color = rowColor, wrap = false)
        }
    }
}