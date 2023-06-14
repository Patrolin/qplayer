package com.patrolin.qplayer.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class DialogState(defaultOpen: Boolean) {
    var open by mutableStateOf(defaultOpen, neverEqualPolicy())
    companion object {
        val Saver: Saver<DialogState, *> = listSaver(
            save = { listOf(it.open) },
            restore = { DialogState(it[0]) },
        )
    }
}
@Composable
fun rememberDialogState(): DialogState {
    return rememberSaveable(saver = DialogState.Saver) {
        DialogState(false)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun useDialog(content: @Composable () -> Unit): DialogState {
    val dialogState = rememberDialogState()
    if (dialogState.open) {
        Dialog(
            onDismissRequest = {
                dialogState.open = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            content()
        }
    }
    return dialogState
}