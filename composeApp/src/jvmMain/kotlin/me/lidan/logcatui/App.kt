package me.lidan.logcatui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.Text

private val AppBackground = Color(0xFF1E1F22)
private val AppBorder = Color(0xFF393B40)
private val ToolbarBackground = Color(0xFF25272C)
private val PanelBackground = Color(0xFF23252A)
private val PanelAltBackground = Color(0xFF2A2D33)
private val SelectedRowBackground = Color(0xFF314158)
private val SoftText = Color(0xFFAEB4BE)
private val Success = Color(0xFF74C365)
private val Warning = Color(0xFFF5C451)
private val ErrorRed = Color(0xFFFF6B68)
private val VerboseGray = Color(0xFF8A8F99)
private val InfoGreen = Color(0xFF7FD37F)
private val IconTint = Color(0xFFC7CDD7)
private val TextFieldBackground = Color(0xFF2B2D31)

private enum class AppIconSymbol {
    Clear,
    Refresh,
    Search,
    Close,
    ScrollToEnd,
}

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
    var deviceSearchQuery by remember { mutableStateOf("") }
    var processSearchQuery by remember { mutableStateOf("") }

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
    val filteredDevices by remember(controller.devices, deviceSearchQuery) {
        derivedStateOf {
            filterDevices(controller.devices, deviceSearchQuery)
        }
    }
    val filteredProcesses by remember(controller.processes, processSearchQuery) {
        derivedStateOf {
            filterProcesses(controller.processes, processSearchQuery)
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
                        devices = filteredDevices,
                        selectedSerial = controller.selectedDeviceSerial,
                        searchQuery = deviceSearchQuery,
                        onSearchChange = { deviceSearchQuery = it },
                        onSelect = controller::selectDevice,
                    )
                }
                Sidebar(
                    title = "Processes",
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                ) {
                    ProcessList(
                        processes = filteredProcesses,
                        selectedPid = controller.selectedProcessPid,
                        searchQuery = processSearchQuery,
                        onSearchChange = { processSearchQuery = it },
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
            ToolbarButtonContent(AppIconSymbol.Clear, "Clear Logs")
        }
        OutlinedButton(onClick = controller::restartAdb) {
            ToolbarButtonContent(AppIconSymbol.Refresh, "Restart ADB")
        }
        SearchField(
            value = controller.searchQuery,
            onValueChange = { controller.searchQuery = it },
            placeholder = "Search tag, message, or raw log line",
            modifier = Modifier.weight(1f),
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
private fun ToolbarButtonContent(icon: AppIconSymbol, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon = icon, modifier = Modifier.size(16.dp))
        Text(label)
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle =
            androidx.compose.ui.text.TextStyle(
                color = Color(0xFFE7EAF0),
                fontFamily = FontFamily.Monospace,
            ),
        placeholder = { Text(placeholder, color = SoftText) },
        leadingIcon = {
            AppIcon(AppIconSymbol.Search, modifier = Modifier.size(16.dp))
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFE7EAF0),
                unfocusedTextColor = Color(0xFFE7EAF0),
                cursorColor = Color(0xFFE7EAF0),
                focusedBorderColor = Color(0xFF4B6EAF),
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = TextFieldBackground,
                unfocusedContainerColor = TextFieldBackground,
                focusedLeadingIconColor = IconTint,
                unfocusedLeadingIconColor = IconTint,
            ),
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
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Filter devices",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
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
}

@Composable
private fun ProcessList(
    processes: List<ProcessDescriptor>,
    selectedPid: Int?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (Int?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Filter processes",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
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
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
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

@Composable
private fun AppIcon(
    icon: AppIconSymbol,
    modifier: Modifier = Modifier,
    tint: Color = IconTint,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        when (icon) {
            AppIconSymbol.Search -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.42f, size.height * 0.42f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.62f, size.height * 0.62f),
                    end = Offset(size.width * 0.86f, size.height * 0.86f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = 35f,
                    sweepAngle = 255f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.68f, size.height * 0.13f),
                    end = Offset(size.width * 0.84f, size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.68f, size.height * 0.13f),
                    end = Offset(size.width * 0.74f, size.height * 0.29f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Clear -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.3f),
                    size = Size(size.width * 0.44f, size.height * 0.5f),
                    cornerRadius = CornerRadius(size.minDimension * 0.05f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.3f),
                    end = Offset(size.width * 0.76f, size.height * 0.3f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.38f, size.height * 0.2f),
                    end = Offset(size.width * 0.62f, size.height * 0.2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.43f, size.height * 0.4f),
                    end = Offset(size.width * 0.43f, size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.57f, size.height * 0.4f),
                    end = Offset(size.width * 0.57f, size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.Close -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.24f),
                    end = Offset(size.width * 0.76f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.76f, size.height * 0.24f),
                    end = Offset(size.width * 0.24f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AppIconSymbol.ScrollToEnd -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.5f, size.height * 0.18f),
                    end = Offset(size.width * 0.5f, size.height * 0.7f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.28f, size.height * 0.5f),
                    end = Offset(size.width * 0.5f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.72f, size.height * 0.5f),
                    end = Offset(size.width * 0.5f, size.height * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.84f),
                    end = Offset(size.width * 0.76f, size.height * 0.84f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun filterDevices(
    devices: List<DeviceDescriptor>,
    query: String,
): List<DeviceDescriptor> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) {
        return devices
    }

    return devices.filter { device ->
        device.serial.lowercase().contains(needle) ||
            device.model.lowercase().contains(needle) ||
            device.state.lowercase().contains(needle)
    }
}

private fun filterProcesses(
    processes: List<ProcessDescriptor>,
    query: String,
): List<ProcessDescriptor> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) {
        return processes
    }

    return processes.filter { process ->
        process.name.lowercase().contains(needle) ||
            process.pid.toString().contains(needle)
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
