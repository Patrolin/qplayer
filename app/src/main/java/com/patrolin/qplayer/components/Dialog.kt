package com.patrolin.qplayer.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class DialogState(val open: Boolean)
@Composable
fun rememberDialogState(defaultOpen: Boolean = false): MutableState<DialogState> {
    return remember() {
        mutableStateOf(DialogState(defaultOpen))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun useDialog(content: @Composable () -> Unit): MutableState<DialogState> {
    val dialogState = rememberDialogState()
    if (dialogState.value.open) {
        Dialog(
            onDismissRequest = {
                dialogState.value = DialogState(false)
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