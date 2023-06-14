package com.patrolin.qplayer.lib

import android.media.MediaPlayer
import android.media.VolumeShaper
import kotlin.concurrent.thread

object GlobalContext {
    // app state (rerender on change)
    var _appState = AppState(true, listOf(), RingBuffer(8), listOf(), listOf(), null,
        PlayingState.STOPPED, true, LoopState.LOOP_ALL, 0)
    // global context (don't rerender on change)
    val mediaPlayer: MediaPlayer = MediaPlayer()
    var onCompletionListener: (() -> Unit)? = null
    private val oclDebounce = debounce(100) { onCompletionListener?.invoke() } // MediaPlayer is garbage and calls this twice
    init {
        mediaPlayer.setOnCompletionListener {
            oclDebounce()
        }
    }
    // song row
    var isPositionThreadRunning = false
    private fun _startPositionThread(setState: (v: AppState) -> Unit) {
        if (!isPositionThreadRunning) {
            isPositionThreadRunning = true
            thread {
                while (_appState.playingState == PlayingState.PLAYING) {
                    val currentPosition = mediaPlayer.currentPosition
                    setState(_appState.withCurrentPosition(currentPosition))
                    Thread.sleep((1000 - (currentPosition % 1000)).toLong())
                }
                setState(_appState.withCurrentPosition(mediaPlayer.currentPosition))
                isPositionThreadRunning = false
            }
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
    private fun _startSong(song: Song, setState: (v: AppState) -> Unit) {
        errPrint("Starting: $song")
        mediaPlayer.reset()
        mediaPlayer.setDataSource(song.path)
        audioFadeIn.apply(VolumeShaper.Operation.PLAY)
        mediaPlayer.prepare()
        mediaPlayer.start()
        _startPositionThread(setState)
    }
    fun startSong(playlist: List<Song>, song: Song?, setState: (v: AppState) -> Unit) {
        val newState = _appState.start(playlist, song)
        setState(newState)
        _startSong(newState.playing!!, setState)
    }
    // top controls
    fun playPauseSong(setState: (v: AppState) -> Unit) {
        when (_appState.playingState) {
            PlayingState.PLAYING -> {
                mediaPlayer.pause()
                setState(_appState.togglePlayingState(PlayingState.PAUSED))
            }
            PlayingState.PAUSED -> {
                audioFadeIn.apply(VolumeShaper.Operation.PLAY)
                mediaPlayer.start()
                _startPositionThread(setState)
                setState(_appState.togglePlayingState(PlayingState.PLAYING))
            }
            PlayingState.STOPPED -> {
                startSong(_appState.songs, null, setState)
            }
        }
    }
    fun playNextSong(setState: (v: AppState) -> Unit) {
        mediaPlayer.stop()
        val newState = _appState.next()
        if (newState.playing != null)
            _startSong(newState.playing, setState)
        setState(newState)
    }
    // bottom controls
    fun stopSong(setState: (v: AppState) -> Unit) {
        mediaPlayer.stop()
        setState(_appState.togglePlayingState(PlayingState.STOPPED, true))
    }
    fun toggleShuffleState(setState: (v: AppState) -> Unit) {
        setState(_appState.toggleShuffle(!_appState.shuffle))
    }
    fun toggleLoopState(setState: (v: AppState) -> Unit) {
        val loopStates = listOf(LoopState.LOOP_ALL, LoopState.LOOP_ONE, LoopState.PLAY_ALL, LoopState.PLAY_ONE)
        val newLoopState = loopStates[(loopStates.indexOf(_appState.loopState) + 1) % loopStates.size]
        setState(_appState.toggleLoopState(newLoopState))
    }
}
enum class PlayingState { STOPPED, PLAYING, PAUSED }
enum class LoopState { LOOP_ALL, LOOP_ONE, PLAY_ALL, PLAY_ONE }
data class AppState(
    val songsLoading: Boolean,
    val songs: List<Song>,
    // playlist
    val playHistory: RingBuffer<Song>,
    val playlist: List<Song>,
    // controls
    val playOrder: List<Song>,
    val playing: Song?,
    val playingState: PlayingState,
    val shuffle: Boolean,
    val loopState: LoopState,
    val currentPosition: Int,
) {
    fun withSongs(newSongsPromise: Promise<List<Song>>): AppState {
        val newSongsLoading = newSongsPromise.state == PromiseState.LOADING
        val newSongs = newSongsPromise.value ?: listOf()
        return AppState(newSongsLoading, newSongs, playHistory, playlist, playOrder, playing, playingState, shuffle, loopState, currentPosition)
    }
    // controls
    private fun _withPlaying(newPlaying: Song?)
        = AppState(songsLoading, songs, playHistory, playlist, playOrder, newPlaying,
            playingState, shuffle, loopState, currentPosition)
    fun start(newPlaylist: List<Song>, newPlaying: Song?): AppState {
        val newState = AppState(songsLoading, songs, playHistory, newPlaylist, listOf(), newPlaying,
            PlayingState.PLAYING, shuffle, loopState, 0).reshuffle()
        return newState._withPlaying(newPlaying ?: newState.playOrder.getOrNull(0))
    }
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
        return AppState(songsLoading, songs, playHistory, playlist, newPlayOrder, playing, playingState, shuffle, loopState, currentPosition)
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
                    newPlayOrder = this._withPlaying(null).reshuffle().playOrder
                    0
                }
            }
            LoopState.LOOP_ONE -> playingIndex
            LoopState.PLAY_ALL -> playingIndex + 1
            LoopState.PLAY_ONE -> playOrder.size
        }
        val newPlaying = if (nextIndex < newPlayOrder.size) newPlayOrder[nextIndex] else null
        val newPlayingState = if (newPlaying != null) playingState else PlayingState.STOPPED
        return AppState(songsLoading, songs, playHistory, playlist, playOrder, newPlaying, newPlayingState, shuffle, loopState, currentPosition)
    }
    fun togglePlayingState(newPlayingState: PlayingState, clearPlaying: Boolean = false): AppState {
        val newPlaying = if (clearPlaying) null else playing
        return AppState(songsLoading, songs, playHistory, playlist, playOrder, newPlaying, newPlayingState, shuffle, loopState, currentPosition)
    }
    fun toggleShuffle(newShuffle: Boolean): AppState
            = AppState(songsLoading, songs, playHistory, playlist, listOf(), playing, playingState, newShuffle, loopState, currentPosition).reshuffle()
    fun toggleLoopState(newLoopState: LoopState)
            = AppState(songsLoading, songs, playHistory, playlist, playOrder, playing, playingState, shuffle, newLoopState, currentPosition)
    fun withCurrentPosition(newCurrentPosition: Int): AppState
            = AppState(songsLoading, songs, playHistory, playlist, playOrder, playing, playingState, shuffle, loopState, newCurrentPosition)
}