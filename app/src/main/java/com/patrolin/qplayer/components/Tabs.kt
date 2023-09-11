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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

data class TabsState(val defaultIndex: Int) {
    var selectedIndex by mutableStateOf(defaultIndex, neverEqualPolicy())
    companion object {
        val Saver: Saver<TabsState, *> = listSaver(
            save = { listOf(it.selectedIndex) },
            restore = { TabsState(it[0]) },
        )
    }
}
@Composable
fun rememberTabsState(defaultIndex: Int = 0): TabsState {
    return rememberSaveable(saver = TabsState.Saver) {
        TabsState(defaultIndex)
    }
}

@Composable
fun Tabs(
    tabsState: TabsState,
    options: List<String>,
    rightBlock: @Composable RowScope.() -> Unit = {},
    block: @Composable (i: Int) -> Unit,
) {
    if (tabsState.selectedIndex >= options.size && tabsState.selectedIndex != tabsState.defaultIndex)
        tabsState.selectedIndex = tabsState.defaultIndex
    Row(Modifier.fillMaxWidth().padding(0.dp, 0.dp, 0.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row() {
            for (i in options.indices) {
                TabButton(options[i], (i == tabsState.selectedIndex)) {
                    tabsState.selectedIndex = i
                }
            }
        }
        Row(Modifier.align(Alignment.CenterVertically)) {
            rightBlock()
        }
    }
    block(tabsState.selectedIndex)
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(0.dp, 0.dp, 0.dp, 2.dp)) {
        Title(text, Modifier.padding(12.dp, 4.dp))
        val highlightAlpha = if (isSelected) 1f else 0f
        Row(Modifier
            .alpha(highlightAlpha)
            .size(20.dp, 1.dp)
            .background(MaterialTheme.colorScheme.primary)
            .align(Alignment.CenterHorizontally)) {}
    }
}