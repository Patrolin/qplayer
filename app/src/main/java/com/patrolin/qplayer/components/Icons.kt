package com.patrolin.qplayer.components

import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
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
    // controls
    @Composable fun PlayIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(androidx.compose.material.icons.Icons.Outlined.PlayArrow, "Play", color, modifier)
    }
    @Composable fun PauseIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_pause_24), "Pause", color, modifier)
    }
    @Composable fun ShuffleIcon(color: Color, modifier: Modifier = Modifier) {
        return BaseIcon(ImageVector.vectorResource(id = R.drawable.baseline_shuffle_24), "Shuffle", color, modifier)
    }
}