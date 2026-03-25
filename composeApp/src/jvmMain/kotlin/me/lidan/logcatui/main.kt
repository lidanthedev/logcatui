package me.lidan.logcatui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Desktop Logcat Viewer",
        state = WindowState(width = 1600.dp, height = 960.dp),
    ) {
        App()
    }
}
