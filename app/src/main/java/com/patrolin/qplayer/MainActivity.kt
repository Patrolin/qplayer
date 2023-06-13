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
import com.patrolin.qplayer.components.SUBTITLE_COLOR
import com.patrolin.qplayer.components.SubTitle
import com.patrolin.qplayer.components.TITLE_COLOR
import com.patrolin.qplayer.components.Text
import com.patrolin.qplayer.components.TextColor
import com.patrolin.qplayer.components.Title
import com.patrolin.qplayer.components.useDialog
import com.patrolin.qplayer.components.useTabs
import com.patrolin.qplayer.lib.Promise
import com.patrolin.qplayer.lib.PromiseState
import com.patrolin.qplayer.lib.READ_PERMISSIONS
import com.patrolin.qplayer.lib.RingBuffer
import com.patrolin.qplayer.lib.Song
import com.patrolin.qplayer.lib.debounce
import com.patrolin.qplayer.lib.errPrint
import com.patrolin.qplayer.lib.getPermissionsText
import com.patrolin.qplayer.lib.getSongsAsync
import com.patrolin.qplayer.lib.onPermissionChange
import com.patrolin.qplayer.lib.openURL
import com.patrolin.qplayer.lib.requestPermissions
import com.patrolin.qplayer.lib.showToast
import com.patrolin.qplayer.ui.theme.QPlayerTheme
import kotlin.concurrent.thread

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
    var _appState = AppState(true, listOf(), listOf(), listOf(), null, PlayingState.STOPPED, RingBuffer(8), false, LoopState.LOOP_ALL, 0)
    // global context (don't rerender on change)
    val mediaPlayer: MediaPlayer = MediaPlayer()
    var isPositionThreadRunning = false
    var onCompletionListener: (() -> Unit)? = null
    private val oclDebounce = debounce(100) { onCompletionListener?.invoke() } // MediaPlayer is garbage and calls this twice
    init {
        mediaPlayer.setOnCompletionListener {
            oclDebounce()
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
enum class LoopState { LOOP_ALL, LOOP_ONE, PLAY_ALL, PLAY_ONE }
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
    val currentPosition: Int,
) {
    fun withSongs(newSongsPromise: Promise<List<Song>>): AppState {
        val newSongsLoading = newSongsPromise.state == PromiseState.LOADING
        val newSongs = newSongsPromise.value ?: listOf()
        return AppState(newSongsLoading, newSongs, playlist, playOrder, playing, playingState, shuffleHistory, shuffle, loopState, currentPosition)
    }
    // controls
    fun start(newPlaylist: List<Song>, newPlaying: Song)
        = AppState(songsLoading, songs, newPlaylist, listOf(), newPlaying,
            PlayingState.PLAYING, shuffleHistory, shuffle, loopState, 0).reshuffle()
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
        // TODO: save last n history
        // TODO: don't repeat last log p songs
        return AppState(songsLoading, songs, playlist, newPlayOrder, playing, playingState, shuffleHistory, shuffle, loopState, currentPosition)
    }
    // TODO: prev
    fun next(): AppState {
        val playingIndex = playOrder.indexOf(playing)
        var newPlayOrder = playOrder
        val nextIndex = when(loopState) {
            LoopState.LOOP_ALL -> {
                val nextIndex = playingIndex + 1
                if (nextIndex < playOrder.size) {
                    nextIndex
                } else {
                    newPlayOrder = this.reshuffle().playOrder
                    if (playOrder.size >= 2) 1 else 0 // TODO: fix .reshuffle()
                }
            }
            LoopState.LOOP_ONE -> playingIndex
            LoopState.PLAY_ALL -> playingIndex + 1
            LoopState.PLAY_ONE -> playOrder.size
        }
        val newPlaying = if (nextIndex < newPlayOrder.size) newPlayOrder[nextIndex] else null
        val newPlayingState = if (newPlaying != null) playingState else PlayingState.STOPPED
        return AppState(songsLoading, songs, playlist, playOrder, newPlaying, newPlayingState, shuffleHistory, shuffle, loopState, currentPosition)
    }
    fun togglePlayingState(newPlayingState: PlayingState, clearPlaying: Boolean = false): AppState {
        val newPlaying = if (clearPlaying) null else playing
        return AppState(songsLoading, songs, playlist, playOrder, newPlaying, newPlayingState, shuffleHistory, shuffle, loopState, currentPosition)
    }
    fun toggleShuffle(newShuffle: Boolean): AppState
        = AppState(songsLoading, songs, playlist, listOf(), playing, playingState, shuffleHistory, newShuffle, loopState, currentPosition).reshuffle()
    fun toggleLoopState(newLoopState: LoopState)
        = AppState(songsLoading, songs, playlist, playOrder, playing, playingState, shuffleHistory, shuffle, newLoopState, currentPosition)
    fun withCurrentPosition(newCurrentPosition: Int): AppState
        = AppState(songsLoading, songs, playlist, playOrder, playing, playingState, shuffleHistory, shuffle, loopState, newCurrentPosition)
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
        synchronized(GlobalContext) {
            GlobalContext._appState = newState
            setNonce(nonce + 1)
        }
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

    fun _startPositionThread() {
        if (!GlobalContext.isPositionThreadRunning) {
            GlobalContext.isPositionThreadRunning = true
            thread {
                while (getState().playingState == PlayingState.PLAYING) {
                    val currentPosition = GlobalContext.mediaPlayer.currentPosition
                    setState(getState().withCurrentPosition(currentPosition))
                    Thread.sleep((1000 - (currentPosition % 1000)).toLong())
                }
                setState(getState().withCurrentPosition(GlobalContext.mediaPlayer.currentPosition))
                GlobalContext.isPositionThreadRunning = false
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
        _startPositionThread()
    }
    fun startSong(song: Song) {
        _startSong(song)
        val state = getState()
        setState(state.start(state.songs, song))
    }
    // top controls
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
                _startPositionThread()
                setState(state.togglePlayingState(PlayingState.PLAYING))
            }
            PlayingState.STOPPED -> {
                val song = state.playing ?: state.songs.getOrNull(0) // TODO: state.next()
                if (song != null) startSong(song)
            }
        }
    }
    fun playNextSong() {
        GlobalContext.mediaPlayer.stop()
        val newState = getState().next()
        if (newState.playing != null)
            _startSong(newState.playing)
        setState(newState)
    }
    GlobalContext.onCompletionListener = {
        playNextSong()
    }
    // bottom controls
    fun stopSong() {
        GlobalContext.mediaPlayer.stop()
        setState(getState().togglePlayingState(PlayingState.STOPPED, true))
    }
    fun toggleShuffleState() {
        val state = getState()
        setState(state.toggleShuffle(!state.shuffle))
    }
    fun toggleLoopState() {
        val state = getState()
        val loopStates = listOf(LoopState.LOOP_ALL, LoopState.LOOP_ONE, LoopState.PLAY_ALL, LoopState.PLAY_ONE)
        val newLoopState = loopStates[(loopStates.indexOf(state.loopState) + 1) % loopStates.size]
        setState(state.toggleLoopState(newLoopState))
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
                            .clickable { playPauseSong() }
                            .align(Alignment.CenterVertically))
                    else
                        Icons.PlayIcon(color = TITLE_COLOR, modifier = Modifier
                            .clickable { playPauseSong() }
                            .align(Alignment.CenterVertically))
                    Icons.NextIcon(color = TITLE_COLOR, modifier = Modifier.clickable { playNextSong() }.align(Alignment.CenterVertically))
                }
                Row(Modifier.padding(0.dp, 4.dp, 0.dp, 0.dp)) {
                    Icons.StopIcon(color = TITLE_COLOR, modifier = Modifier.clickable { stopSong() }.align(Alignment.CenterVertically))
                    val shuffleIconModifier = Modifier
                        .clickable { toggleShuffleState() }
                        .align(Alignment.CenterVertically)
                    if (getState().shuffle) {
                        Icons.ShuffleIcon(color = TITLE_COLOR, modifier = shuffleIconModifier)
                    } else {
                        Icons.ShuffleOffIcon(color = TITLE_COLOR, modifier = shuffleIconModifier)
                    }
                    val loopIconModifier = Modifier
                        .clickable { toggleLoopState() }
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
        //Column(Modifier.width(24.dp)) {
            SubTitle("${i+1}",
                Modifier
                    .align(Alignment.CenterVertically)
                    .padding(6.dp, 0.dp, 0.dp, 0.dp), color = rowColor)
        //}
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