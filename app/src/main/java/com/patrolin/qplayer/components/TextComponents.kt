package com.patrolin.qplayer.components

import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TITLE_COLOR @Composable get() = MaterialTheme.colorScheme.onSurface
val SUBTITLE_COLOR @Composable get() = getSubtitleColor(TITLE_COLOR)
@Composable fun getSubtitleColor(color: Color) = color.copy(0.6f)

val TITLE_SIZE = 16.sp
val TEXT_SIZE = 15.sp

enum class TextColor {
    DEFAULT,
    PRIMARY,
    SECONDARY;
    @Composable
    fun getColor(defaultColor: Color): Color {
        return when (this) {
            DEFAULT -> defaultColor
            PRIMARY -> MaterialTheme.colorScheme.primary
            SECONDARY -> MaterialTheme.colorScheme.secondary
        }
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, color: TextColor = TextColor.DEFAULT, wrap: Boolean = true) {
    val scrollState = rememberScrollState()
    androidx.compose.material3.Text(
        text,
        fontSize = TITLE_SIZE,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
        color = color.getColor(TITLE_COLOR),
        softWrap = wrap,
        modifier = if (wrap) modifier else modifier.horizontalScroll(scrollState),
    )
}
@Composable
fun SubTitle(text: String, modifier: Modifier = Modifier, color: TextColor = TextColor.DEFAULT, wrap: Boolean = true) {
    val scrollState = rememberScrollState()
    androidx.compose.material3.Text(
        text,
        fontSize = TEXT_SIZE,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
        color = getSubtitleColor(color.getColor(TITLE_COLOR)),
        softWrap = wrap,
        modifier = if (wrap) modifier else modifier.horizontalScroll(scrollState),
    )
}
@Composable
fun Text(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text,
        fontSize = TEXT_SIZE,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Serif,
        color = TITLE_COLOR,
    )
}
@Composable
fun Monospace(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text,
        fontSize = TEXT_SIZE,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        color = TITLE_COLOR,
    )
}

fun errPrint(message: String) {
    Log.d("AYAYA", message)
}