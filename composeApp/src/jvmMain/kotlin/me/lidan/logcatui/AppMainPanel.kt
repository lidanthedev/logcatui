package me.lidan.logcatui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun MainPanel(
    controller: LogcatController,
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().background(AppBackground)) {
        LogHeader(
            count = logs.size,
            isStreaming = controller.isStreaming,
            selectedProcess = controller.selectedProcessPid,
            onRefresh = controller::refreshLogs,
            refreshEnabled = controller.selectedDeviceSerial != null,
            onScrollToEnd = { controller.autoScrollToBottom = true },
        )
        HorizontalDivider(color = AppBorder)
        SwingPanel(
            background = AppBackground,
            modifier = Modifier.weight(1f).fillMaxSize(),
            factory = { LogTextView { controller.autoScrollToBottom = it } },
            update = { it.updateLogs(logs, controller.autoScrollToBottom) },
        )
    }
}

@Composable
private fun LogHeader(
    count: Int,
    isStreaming: Boolean,
    selectedProcess: Int?,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
    onScrollToEnd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Log Output",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    buildString {
                        append(if (isStreaming) "Streaming" else "Idle")
                        append(" • ")
                        append("$count rows")
                        selectedProcess?.let {
                            append(" • PID ")
                            append(it)
                        }
                    },
                color = SoftText,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = refreshEnabled) {
                ToolbarButtonContent(AppIconSymbol.Refresh, "Refresh Logs")
            }
            OutlinedButton(onClick = onScrollToEnd) {
                ToolbarButtonContent(AppIconSymbol.ScrollToEnd, "Scroll to End")
            }
        }
    }
}
