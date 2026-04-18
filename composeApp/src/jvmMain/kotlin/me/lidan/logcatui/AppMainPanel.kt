package me.lidan.logcatui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun MainPanel(
    controller: LogcatController,
    logs: List<LogEntry>,
    selectedLog: LogEntry?,
    lazyListState: LazyListState,
    onCopyRawLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun moveSelection(delta: Int): Boolean {
        if (logs.isEmpty()) return false

        val currentIndex = selectedLog?.let { selected -> logs.indexOfFirst { it.id == selected.id } } ?: -1
        val targetIndex =
            when {
                currentIndex == -1 && delta > 0 -> 0
                currentIndex == -1 && delta < 0 -> logs.lastIndex
                else -> (currentIndex + delta).coerceIn(0, logs.lastIndex)
            }

        controller.autoScrollToBottom = false
        controller.selectLog(logs[targetIndex])
        scope.launch {
            lazyListState.scrollToItemIfNotVisible(targetIndex)
        }
        return true
    }

    Column(
        modifier =
            modifier.fillMaxHeight()
                .background(AppBackground),
    ) {
        LogHeader(
            count = logs.size,
            isStreaming = controller.isStreaming,
            selectedProcess = controller.selectedProcessPid,
            onScrollToEnd = {
                controller.autoScrollToBottom = true
                scope.launch {
                    if (logs.isNotEmpty()) {
                        lazyListState.animateScrollToItem(logs.lastIndex)
                    }
                }
            },
        )
        HorizontalDivider(color = AppBorder)
        TableHeader()
        HorizontalDivider(color = AppBorder)
        Box(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        if (it.isCtrlPressed && it.key == Key.C) {
                            selectedLog?.rawLine?.let(onCopyRawLog)
                            return@onPreviewKeyEvent selectedLog != null
                        }
                        when (it.key) {
                            Key.DirectionUp -> moveSelection(delta = -1)
                            Key.DirectionDown -> moveSelection(delta = 1)
                            else -> false
                        }
                    },
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            ) {
                items(logs, key = LogEntry::id) { entry ->
                    LogRow(
                        entry = entry,
                        selected = entry.id == selectedLog?.id,
                        onClick = {
                            controller.autoScrollToBottom = false
                            controller.selectLog(entry)
                            focusRequester.requestFocus()
                        },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LogHeader(
    count: Int,
    isStreaming: Boolean,
    selectedProcess: Int?,
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
        OutlinedButton(onClick = onScrollToEnd) {
            ToolbarButtonContent(AppIconSymbol.ScrollToEnd, "Scroll to End")
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(PanelBackground)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderCell("Timestamp", 150.dp)
        HeaderCell("Level", 50.dp)
        HeaderCell("Tag", 200.dp)
        HeaderCell("Message", null)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, width: Dp?) {
    Text(
        text = text,
        color = SoftText,
        fontFamily = FontFamily.Monospace,
        modifier = if (width != null) Modifier.width(width) else Modifier.weight(1f),
    )
}

@Composable
private fun LogRow(
    entry: LogEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val levelColor = levelColor(entry.level)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (selected) SelectedRowBackground else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MonoText(entry.timestamp, modifier = Modifier.width(150.dp), color = SoftText)
        MonoText(
            entry.level.token.toString(),
            modifier = Modifier.width(50.dp),
            color = levelColor,
            fontWeight = FontWeight.Bold,
        )
        MonoText(entry.tag, modifier = Modifier.width(200.dp), color = levelColor)
        MonoText(entry.message, modifier = Modifier.weight(1f), color = Color(0xFFE7EAF0))
    }
}

@Composable
private fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun DetailPanel(
    entry: LogEntry,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 180.dp, max = 280.dp)
                .background(PanelAltBackground)
                .border(1.dp, AppBorder)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${entry.tag} • PID ${entry.pid} • ${entry.timestamp}", fontWeight = FontWeight.SemiBold)
                Text("Level ${entry.level.label}", color = levelColor(entry.level))
            }
            OutlinedButton(onClick = onClose) {
                ToolbarButtonContent(AppIconSymbol.Close, "Hide")
            }
        }
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .background(AppBackground)
                    .border(1.dp, AppBorder)
                    .padding(12.dp),
        ) {
            AppTextField(
                value = entry.rawLine,
                onValueChange = {},
                placeholder = "Log Line",
                readOnly = true,
                modifier = Modifier,
            )
        }
    }
}

@Composable
internal fun ToastMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(PanelAltBackground, RoundedCornerShape(8.dp))
                .border(1.dp, AppBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(message, color = Color(0xFFE7EAF0))
    }
}
