package me.lidan.logcatui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

@Composable
fun App() {
    val controller = remember { LogcatController(AdbLogcatService()) }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }

    IntUiTheme(isDark = true) {
        LogcatViewer(controller = controller)
    }
}
