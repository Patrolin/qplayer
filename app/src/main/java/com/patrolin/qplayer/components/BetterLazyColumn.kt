package com.patrolin.qplayer.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.patrolin.qplayer.lib.errPrint
import com.patrolin.qplayer.lib.getScreenSize
import com.patrolin.qplayer.lib.getTimeStamp
import kotlin.math.min

@Composable
fun <T>BetterLazyColumn(
    scrollState: ScrollState,
    items: List<T>,
    rowHeight: Int,
    modifier: Modifier = Modifier,
    maxIndexOffset: Int = 0,
    block: @Composable ColumnScope.(index: Int, item: T) -> Unit
) {
    val startTime = getTimeStamp()
    val scrollPosition = scrollState.value.toFloat() / LocalDensity.current.density
    val minIndex = (scrollPosition / rowHeight).toInt()
    val screenHeight = getScreenSize().bottom / LocalDensity.current.density
    val maxIndex = min(items.size, ((scrollPosition + screenHeight) / rowHeight).toInt() + maxIndexOffset)
    errPrint("minIndex: $minIndex, maxIndex: $maxIndex")
    errPrint("time.1: ${getTimeStamp() - startTime}")
    Column(modifier.verticalScroll(scrollState).height((items.size * rowHeight).dp)) {
        Row(Modifier.height((minIndex * rowHeight).dp).fillMaxWidth()) {}
        errPrint("time.2: ${getTimeStamp() - startTime}")
        val columnScope = this
        for (i in minIndex until maxIndex) {
            block(columnScope, i, items[i])
        }
        errPrint("time.3: ${getTimeStamp() - startTime}")
        Row(Modifier.height(((items.size - maxIndex - 1) * rowHeight).dp).fillMaxWidth()) {}
    }
    errPrint("time.4: ${getTimeStamp() - startTime}")
}