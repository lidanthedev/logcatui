package me.lidan.logcatui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val AppBackground = Color(0xFF1E1F22)
private val AppBorder = Color(0xFF393B40)
private val ToolbarBackground = Color(0xFF25272C)
private val PanelBackground = Color(0xFF23252A)
private val PanelAltBackground = Color(0xFF2A2D33)
private val SelectedRowBackground = Color(0xFF314158)
private val RowHoverBackground = Color(0xFF2C3037)
private val SoftText = Color(0xFFAEB4BE)
private val Success = Color(0xFF74C365)
private val Warning = Color(0xFFF5C451)
private val ErrorRed = Color(0xFFFF6B68)
private val VerboseGray = Color(0xFF8A8F99)
private val InfoGreen = Color(0xFF7FD37F)

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogcatViewer(controller: LogcatController) {
    val lazyListState = rememberLazyListState()
    val searchPatternResult by remember(controller.searchQuery, controller.regexEnabled) {
        derivedStateOf {
            if (controller.searchQuery.isBlank()) {
                null
            } else if (controller.regexEnabled) {
                runCatching { Regex(controller.searchQuery, RegexOption.IGNORE_CASE) }
            } else {
                null
            }
        }
    }
    val filteredLogs by remember(controller.logs, controller.searchQuery, controller.regexEnabled) {
        derivedStateOf {
            val query = controller.searchQuery.trim()
            val regex = searchPatternResult?.getOrNull()
            controller.logs.filter { entry ->
                when {
                    query.isEmpty() -> true
                    controller.regexEnabled && regex != null ->
                        regex.containsMatchIn(entry.tag) ||
                            regex.containsMatchIn(entry.message) ||
                            regex.containsMatchIn(entry.rawLine)

                    controller.regexEnabled -> true
                    else -> {
                        val needle = query.lowercase()
                        entry.tag.lowercase().contains(needle) ||
                            entry.message.lowercase().contains(needle) ||
                            entry.rawLine.lowercase().contains(needle)
                    }
                }
            }
        }
    }
    val selectedLog by remember(filteredLogs, controller.selectedLogId) {
        derivedStateOf { filteredLogs.firstOrNull { it.id == controller.selectedLogId } }
    }

    LaunchedEffect(filteredLogs.size, controller.autoScrollToBottom) {
        if (controller.autoScrollToBottom && filteredLogs.isNotEmpty()) {
            lazyListState.scrollToItem(filteredLogs.lastIndex)
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(AppBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(
                controller = controller,
                regexError = searchPatternResult?.exceptionOrNull()?.message,
            )
            HorizontalDivider(color = AppBorder)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Sidebar(
                    title = "Devices",
                    modifier = Modifier.width(250.dp).fillMaxHeight(),
                ) {
                    DeviceList(
                        devices = controller.devices,
                        selectedSerial = controller.selectedDeviceSerial,
                        onSelect = controller::selectDevice,
                    )
                }
                Sidebar(
                    title = "Processes",
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                ) {
                    ProcessList(
                        processes = controller.processes,
                        selectedPid = controller.selectedProcessPid,
                        onSelect = controller::selectProcess,
                    )
                }
                MainPanel(
                    controller = controller,
                    logs = filteredLogs,
                    selectedLog = selectedLog,
                    lazyListState = lazyListState,
                    modifier = Modifier.weight(1f),
                )
            }
            if (controller.detailExpanded) {
                selectedLog?.let { entry ->
                    DetailPanel(
                        entry = entry,
                        onClose = { controller.detailExpanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    controller: LogcatController,
    regexError: String?,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(ToolbarBackground)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DefaultButton(onClick = controller::clearDeviceLogs) {
            ToolbarButtonContent(AllIconsKeys.Actions.GC, "Clear Logs")
        }
        OutlinedButton(onClick = controller::restartAdb) {
            ToolbarButtonContent(AllIconsKeys.Actions.ForceRefresh, "Restart ADB")
        }
        SearchField(
            value = controller.searchQuery,
            onValueChange = { controller.searchQuery = it },
            modifier = Modifier.weight(1f).height(32.dp),
        )
        Checkbox(
            checked = controller.regexEnabled,
            onCheckedChange = { controller.regexEnabled = it },
        )
        Text("Regex", color = SoftText)
        Checkbox(
            checked = controller.autoScrollToBottom,
            onCheckedChange = { controller.autoScrollToBottom = it },
        )
        Text("Auto-scroll", color = SoftText)
        LevelDropdown(
            selected = controller.selectedLevel,
            onSelect = controller::selectLevel,
        )
        DeviceDropdown(
            devices = controller.devices,
            selectedSerial = controller.selectedDeviceSerial,
            onSelect = controller::selectDevice,
        )
        Text(
            text = regexError ?: controller.statusMessage,
            color = if (regexError == null) SoftText else Warning,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}

@Composable
private fun ToolbarButtonContent(icon: org.jetbrains.jewel.ui.icon.IconKey, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label)
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = androidx.compose.ui.text.input.TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        modifier = modifier,
        placeholder = { Text("Search tag, message, or raw log line") },
        leadingIcon = {
            Icon(AllIconsKeys.Actions.Find, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun LevelDropdown(
    selected: LogLevel,
    onSelect: (LogLevel) -> Unit,
) {
    val popupManager = remember { PopupManager() }
    ComboBox(
        labelText = selected.label,
        popupManager = popupManager,
        modifier = Modifier.width(120.dp).height(32.dp),
    ) {
        Column(
            modifier =
                Modifier.background(PanelBackground)
                    .border(1.dp, AppBorder)
                    .width(150.dp)
        ) {
            LogLevel.dropdownValues.forEach { level ->
                DropdownRow(
                    text = level.label,
                    selected = level == selected,
                    onClick = {
                        onSelect(level)
                        popupManager.setPopupVisible(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceDropdown(
    devices: List<DeviceDescriptor>,
    selectedSerial: String?,
    onSelect: (String?) -> Unit,
) {
    val popupManager = remember { PopupManager() }
    val selectedLabel = devices.firstOrNull { it.serial == selectedSerial }?.label ?: "No device"
    ComboBox(
        labelText = selectedLabel,
        popupManager = popupManager,
        modifier = Modifier.width(280.dp).height(32.dp),
        enabled = devices.isNotEmpty(),
    ) {
        Column(
            modifier =
                Modifier.background(PanelBackground)
                    .border(1.dp, AppBorder)
                    .width(320.dp)
        ) {
            devices.forEach { device ->
                DropdownRow(
                    text = "${device.model}  ${device.serial}",
                    secondaryText = device.state,
                    selected = device.serial == selectedSerial,
                    onClick = {
                        onSelect(device.serial)
                        popupManager.setPopupVisible(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun Sidebar(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier.background(PanelBackground)
                .border(width = 1.dp, color = AppBorder)
                .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = SoftText,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        HorizontalDivider(color = AppBorder, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceDescriptor>,
    selectedSerial: String?,
    onSelect: (String?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(devices, key = DeviceDescriptor::serial) { device ->
            SidebarRow(
                title = device.model,
                subtitle = "${device.serial} • ${device.state}",
                selected = device.serial == selectedSerial,
                onClick = { onSelect(device.serial) },
            )
        }
    }
}

@Composable
private fun ProcessList(
    processes: List<ProcessDescriptor>,
    selectedPid: Int?,
    onSelect: (Int?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "all-processes") {
            SidebarRow(
                title = "All processes",
                subtitle = "Show logs from every process",
                selected = selectedPid == null,
                onClick = { onSelect(null) },
            )
        }
        items(processes, key = ProcessDescriptor::pid) { process ->
            SidebarRow(
                title = process.name,
                subtitle = "PID ${process.pid}",
                selected = process.pid == selectedPid,
                onClick = { onSelect(process.pid) },
            )
        }
    }
}

@Composable
private fun SidebarRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (selected) SelectedRowBackground else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = subtitle, color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MainPanel(
    controller: LogcatController,
    logs: List<LogEntry>,
    selectedLog: LogEntry?,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxHeight()
                .background(AppBackground),
    ) {
        LogHeader(
            count = logs.size,
            isStreaming = controller.isStreaming,
            selectedProcess = controller.selectedProcessPid,
        )
        HorizontalDivider(color = AppBorder)
        TableHeader()
        HorizontalDivider(color = AppBorder)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            ) {
                items(logs, key = LogEntry::id) { entry ->
                    LogRow(
                        entry = entry,
                        selected = entry.id == selectedLog?.id,
                        onClick = { controller.selectLog(entry) },
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun RowScope.HeaderCell(text: String, width: androidx.compose.ui.unit.Dp?) {
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
    val levelColor =
        when (entry.level) {
            LogLevel.Verbose -> VerboseGray
            LogLevel.Debug -> SoftText
            LogLevel.Info -> InfoGreen
            LogLevel.Warn -> Warning
            LogLevel.Error, LogLevel.Assert -> ErrorRed
        }
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
        MonoText(entry.level.token.toString(), modifier = Modifier.width(50.dp), color = levelColor, fontWeight = FontWeight.Bold)
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
private fun DetailPanel(
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
                ToolbarButtonContent(AllIconsKeys.Actions.Close, "Hide")
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
            Text(
                text = entry.rawLine,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE7EAF0),
            )
        }
    }
}

@Composable
private fun DropdownRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    secondaryText: String? = null,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (selected) SelectedRowBackground else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text)
        secondaryText?.let { Text(it, color = SoftText) }
    }
}

private fun levelColor(level: LogLevel): Color =
    when (level) {
        LogLevel.Verbose -> VerboseGray
        LogLevel.Debug -> SoftText
        LogLevel.Info -> Success
        LogLevel.Warn -> Warning
        LogLevel.Error, LogLevel.Assert -> ErrorRed
    }
