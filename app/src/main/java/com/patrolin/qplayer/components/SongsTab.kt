package com.patrolin.qplayer.components

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrolin.qplayer.SongRow
import com.patrolin.qplayer.lib.AppState
import com.patrolin.qplayer.lib.GlobalContext
import com.patrolin.qplayer.lib.PlayingState
import com.patrolin.qplayer.lib.Song
import com.patrolin.qplayer.lib.errPrint
import com.patrolin.qplayer.lib.getPermissionsText
import com.patrolin.qplayer.lib.showToast
import kotlinx.coroutines.Job

@Composable
fun ColumnScope.SongsTab(
    state: AppState, setState: (AppState) -> Unit,
    haveReadPermissions: Boolean, playlist: List<Song>, isPlayingTab: Boolean, switchAndScrollToPlaying: () -> Job, scrollState: ScrollState,
) {
    if (!haveReadPermissions) {
        Text(getPermissionsText("read"), Modifier.padding(6.dp, 0.dp).weight(1f))
    } else if (state.songsLoading) {
        Text("Loading...", Modifier.padding(6.dp, 0.dp).weight(1f))
    } else if (playlist.isEmpty()) {
        val noSongsMessage = if (isPlayingTab) "Start playing in another tab!"
            else "No songs yet, try adding a Youtube playlist or adding songs to your Music folder!"
        Text(noSongsMessage, Modifier.padding(6.dp, 0.dp).weight(1f))
    } else {
        BetterLazyColumn(scrollState, playlist, 54, Modifier.weight(1f), maxIndexOffset = -2) {index, song ->
            val isPlaying = (song == state.playing) && (state.playingState == PlayingState.PLAYING)
            SongRow(index, song.name, song.artist, isPlaying) {
                if (isPlaying) {
                    GlobalContext.stopSong(setState)
                } else {
                    try {
                        GlobalContext.startSong(state.songs, song, setState, switchAndScrollToPlaying)
                    } catch (error: Exception) {
                        errPrint("$error")
                        showToast("$error", Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }
}