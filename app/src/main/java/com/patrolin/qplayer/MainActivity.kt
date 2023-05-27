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
import com.patrolin.qplayer.lib.RingBuffer
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
    var _appState = AppState(true, listOf(), listOf(), listOf(), null, PlayingState.STOPPED, RingBuffer(8), false, LoopState.NONE)
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
}
enum class PlayingState { STOPPED, PLAYING, PAUSED }
enum class LoopState { NONE, ONE, ALL }
data class AppState(
    val songsLoading: Boolean,
    val songs: List<Song>,
    // playlist
    val playlist: List<Song>,
    // controls
    val playOrder: List<Song>,
    val playing: Song?,
    val playingState: PlayingState,
    val shuffleHistory: RingBuffer<Song>,
    val shuffle: Boolean,
    val loopState: LoopState,
) {
    fun withSongs(newSongs: Promise<List<Song>>): AppState
        = AppState(newSongs.state == PromiseState.LOADING, newSongs.value ?: listOf(), playlist, playOrder, playing, playingState, shuffleHistory, shuffle, loopState)
    // controls
    fun start(newPlaylist: List<Song>, newPlaying: Song, newShuffle: Boolean? = null)
        = AppState(songsLoading, songs, newPlaylist, listOf(), newPlaying, PlayingState.PLAYING, shuffleHistory, newShuffle ?: shuffle, loopState).reshuffle()
    private fun reshuffle(): AppState {
        val newPlayOrder = if (shuffle) {
            val newPlayOrder = playlist.shuffled().toMutableList()
            val playingIndex = newPlayOrder.indexOf(playing)
            if (playingIndex != -1) {
                val tmp = newPlayOrder[0]
                newPlayOrder[0] = newPlayOrder[playingIndex]
                newPlayOrder[playingIndex] = tmp
            }
            newPlayOrder
        } else {
            playlist
        }
        // TODO: don't repeat last log n songs (cross playlist?)
        return AppState(songsLoading, songs, playlist, newPlayOrder, playing, playingState, shuffleHistory, shuffle, loopState)
    }
    fun next(): AppState {
        val playingIndex = playOrder.indexOf(playing)
        var newPlayOrder = playOrder
        val nextIndex = when(loopState) {
            LoopState.NONE -> playingIndex + 1
            LoopState.ONE -> playingIndex
            LoopState.ALL -> {
                val nextIndex = playingIndex + 1
                if (nextIndex < playOrder.size) {
                    nextIndex
                } else {
                    newPlayOrder = this.reshuffle().playOrder
                    0
                }
            }
        }
        errPrint("state: $this, playingIndex: $playingIndex, newPlayOrder: $newPlayOrder, nextIndex: $nextIndex")
        val newPlaying = if (nextIndex < newPlayOrder.size) newPlayOrder[nextIndex] else null
        val newPlayingState = if (newPlaying != null) playingState else PlayingState.STOPPED
        return AppState(songsLoading, songs, playlist, playOrder, newPlaying, newPlayingState, shuffleHistory, shuffle, loopState)
    }
    fun togglePlayingState(newPlayingState: PlayingState)
        = AppState(songsLoading, songs, playlist, playOrder, playing, newPlayingState, shuffleHistory, shuffle, loopState)
    fun toggleShuffle(newShuffle: Boolean): AppState
        = AppState(songsLoading, songs, playlist, listOf(), playing, PlayingState.PLAYING, shuffleHistory, newShuffle, loopState).reshuffle()
    fun toggleLoopState(newLoopState: LoopState)
        = AppState(songsLoading, songs, playlist, playOrder, playing, playingState, shuffleHistory, shuffle, newLoopState)
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

    fun _startSong(song: Song) {
        errPrint("Playing: $song")
        GlobalContext.mediaPlayer.reset()
        GlobalContext.mediaPlayer.setDataSource(song.path)
        GlobalContext.audioFadeIn.apply(VolumeShaper.Operation.PLAY)
        GlobalContext.mediaPlayer.prepare()
        GlobalContext.mediaPlayer.start()
    }
    fun startSong(song: Song) {
        _startSong(song)
        val state = getState()
        setState(state.start(state.songs, song))
    }
    fun playPauseSong() {
        val state = getState()
        when (state.playingState) {
            PlayingState.PLAYING -> {
                GlobalContext.mediaPlayer.pause()
                setState(state.togglePlayingState(PlayingState.PAUSED))
            }
            PlayingState.PAUSED -> {
                GlobalContext.audioFadeIn.apply(VolumeShaper.Operation.PLAY)
                GlobalContext.mediaPlayer.start()
                setState(state.togglePlayingState(PlayingState.PLAYING))
            }
            PlayingState.STOPPED -> {
                val song = state.playing ?: state.songs.getOrNull(0)
                if (song != null) startSong(song)
            }
        }
    }
    fun stopSong() {
        errPrint("Stopping song")
        GlobalContext.mediaPlayer.stop()
        setState(getState().togglePlayingState(PlayingState.STOPPED))
    }
    GlobalContext.onCompletionListener = {
        val state = getState()
        errPrint("Song completed: ${state.playing}")
        val newState = state.next()
        if (newState.playingState == PlayingState.PLAYING) {
            _startSong(newState.playing!!)
        }
        setState(newState)
    }
    fun toggleLoopState() {
        val state = getState()
        val newLoopState = when(state.loopState) {
            LoopState.NONE -> LoopState.ONE
            LoopState.ONE -> LoopState.ALL
            LoopState.ALL -> LoopState.NONE
        }
        setState(state.toggleLoopState(newLoopState))
    }
    // TODO: https://developer.android.com/guide/topics/media-apps/audio-focus#audio-focus-change
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
                        val playingIndex = getState().playOrder.indexOf(getState().playing)
                        LazyColumn(Modifier.weight(1f)) {
                            items(
                                count = getState().songs.size,
                                key = { it },
                                itemContent = {
                                    val state = getState()
                                    val song = state.songs[it]
                                    val isPlaying = (it == playingIndex) && (state.playingState == PlayingState.PLAYING)
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
                Title(getState().playing?.name.orEmpty())
                SubTitle(getState().playing?.artist.orEmpty())
            }
            if (getState().playingState == PlayingState.PLAYING)
                Icons.PauseIcon(color = TITLE_COLOR, modifier = Modifier.clickable { playPauseSong() }.align(Alignment.CenterVertically))
            else
                Icons.PlayIcon(color = TITLE_COLOR, modifier = Modifier.clickable { playPauseSong() }.align(Alignment.CenterVertically))
            Icons.ShuffleIcon(color = TITLE_COLOR, modifier = Modifier.align(Alignment.CenterVertically)) // TODO: shuffle toggle
            when (getState().loopState) {
                LoopState.NONE -> Icons.LoopNoneIcon(color = TITLE_COLOR, modifier = Modifier.clickable { toggleLoopState() }.align(Alignment.CenterVertically))
                LoopState.ONE -> Icons.LoopOneIcon(color = TITLE_COLOR, modifier = Modifier.clickable { toggleLoopState() }.align(Alignment.CenterVertically))
                LoopState.ALL -> Icons.LoopAllIcon(color = TITLE_COLOR, modifier = Modifier.clickable { toggleLoopState() }.align(Alignment.CenterVertically))
            }
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