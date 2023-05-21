package com.patrolin.qplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

data class TabsState(val selectedIndex: Int, val defaultIndex: Int) {
    fun withIndex(index: Int): TabsState {
        return TabsState(index, defaultIndex)
    }
}

@Composable
fun rememberTabsState(defaultIndex: Int = 0): MutableState<TabsState> {
    return remember() {
        mutableStateOf(TabsState(defaultIndex, defaultIndex))
    }
}

@Composable
fun useTabs(
    defaultIndex: Int,
    options: List<String>,
    rightBlock: @Composable RowScope.() -> Unit = {},
    block: @Composable (i: Int) -> Unit,
) {
    val tabsState = rememberTabsState(defaultIndex)
    if (tabsState.value.selectedIndex >= options.size)
        tabsState.value = tabsState.value.withIndex(tabsState.value.defaultIndex)
    Row(Modifier.fillMaxWidth().padding(0.dp, 0.dp, 0.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row() {
            for (i in options.indices) {
                TabButton(options[i], (i == tabsState.value.selectedIndex)) {
                    tabsState.value = tabsState.value.withIndex(i)
                }
            }
        }
        Row(Modifier.align(Alignment.CenterVertically)) {
            rightBlock()
        }
    }
    block(tabsState.value.selectedIndex)
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        Title(text, Modifier.padding(8.dp, 4.dp))
        val highlightAlpha = if (isSelected) 1f else 0f
        Row(Modifier
            .alpha(highlightAlpha)
            .size(24.dp, 1.dp)
            .background(MaterialTheme.colorScheme.primary)
            .align(Alignment.CenterHorizontally)) {}
    }
}