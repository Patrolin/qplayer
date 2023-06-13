package com.patrolin.qplayer.components

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.patrolin.qplayer.R

object Icons {
    @Composable fun BaseIcon(vector: ImageVector, alt: String, color: Color, modifier: Modifier) {
        return Icon(vector, alt, modifier, tint = color)
    }
    // about
    @Composable fun AboutIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(androidx.compose.material.icons.Icons.Outlined.Info, "About", color, modifier)
    }
    @Composable fun GithubIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.github), "Github", color, modifier)
    }
    // top controls
    @Composable fun PrevIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_skip_previous_24), "Previous", color, modifier)
    }
    @Composable fun PlayIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(androidx.compose.material.icons.Icons.Filled.PlayArrow, "Play", color, modifier)
    }
    @Composable fun PauseIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_pause_24), "Pause", color, modifier)
    }
    @Composable fun NextIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_skip_next_24), "Next", color, modifier)
    }
    // bottom controls
    @Composable fun StopIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_stop_24), "Stop", color, modifier)
    }
    @Composable fun ShuffleIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_shuffle_24), "Shuffle", color, modifier)
    }
    @Composable fun ShuffleOffIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_shuffle_off_24), "Shuffle off", color, modifier)
    }
    @Composable fun LoopAllIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_loop_all_24), "Loop all", color, modifier)
    }
    @Composable fun LoopOneIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_loop_one_24), "Loop one", color, modifier)
    }
    @Composable fun PlayAllIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_play_all_24), "Play all", color, modifier)
    }
    @Composable fun PlayOneIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_play_one_24), "Play one", color, modifier)
    }
}