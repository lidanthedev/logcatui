package me.lidan.logcatui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.text.RegexOption
import kotlinx.coroutines.launch
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
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
private val TextFieldFocusedBorder = Color(0xFF4B6EAF)

private enum class AppIconSymbol {
    Clear,
    Refresh,
    Search,
    Close,
    ScrollToEnd,
    ChevronDown,
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
    val focusManager = LocalFocusManager.current
    var deviceSearchQuery by remember { mutableStateOf("") }
    var processSearchQuery by remember { mutableStateOf("") }

    val searchMatcherResult by remember(controller.searchQuery, controller.regexEnabled) {
        derivedStateOf {
            buildLogSearchMatcher(controller.searchQuery, controller.regexEnabled)
        }
    }
    val filteredLogs by remember(controller.logs, controller.searchQuery, controller.regexEnabled) {
        derivedStateOf {
            val matcher = searchMatcherResult.getOrNull()
            controller.logs.filter { entry -> matcher?.matches(entry) ?: true }
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
    val lastFilteredLogId by remember(filteredLogs) {
        derivedStateOf { filteredLogs.lastOrNull()?.id }
    }

    LaunchedEffect(lastFilteredLogId, controller.autoScrollToBottom) {
        if (controller.autoScrollToBottom && lastFilteredLogId != null) {
            lazyListState.scrollToItem(filteredLogs.lastIndex)
        }
    }
    LaunchedEffect(lazyListState) {
        var wasScrolling = false
        snapshotFlow { lazyListState.isScrollInProgress to lazyListState.isAtBottom() }
            .collect { (scrolling, atBottom) ->
                if (scrolling || wasScrolling) {
                    controller.autoScrollToBottom = atBottom
                }
                wasScrolling = scrolling
            }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(AppBackground)
                .clearSelectionOnBackgroundTap(focusManager) { controller.selectLog(null) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(
                controller = controller,
                regexError = searchMatcherResult.exceptionOrNull()?.message,
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
                        selectedProcessName = controller.selectedProcessName,
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
            onSubmit = controller::saveCurrentFilterToHistory,
            placeholder = "Search tag, message, or raw log line",
            modifier = Modifier.weight(1f),
        )
        FilterHistoryDropdown(
            history = controller.filterHistory,
            onSelect = controller::applyFilterFromHistory,
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
    onSubmit: () -> Unit = {},
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value,
        onValueChange,
        modifier,
        placeholder,
        onSubmit = onSubmit,
        icon = { AppIcon(AppIconSymbol.Search, modifier = Modifier.size(14.dp)) })
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    readOnly: Boolean = false,
    onSubmit: () -> Unit = {},
    icon: @Composable RowScope.() -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = readOnly,
        textStyle =
            TextStyle(
                color = Color(0xFFE7EAF0),
                fontFamily = FontFamily.Monospace,
            ),
        cursorBrush = SolidColor(Color(0xFFE7EAF0)),
        interactionSource = remember { MutableInteractionSource() },
        modifier =
            modifier
                .height(34.dp)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                }
                .border(
                    width = 1.dp,
                    color = if (focused) TextFieldFocusedBorder else AppBorder,
                    shape = RoundedCornerShape(8.dp),
                )
                .background(TextFieldBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon()
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun LevelDropdown(
    selected: LogLevel,
    onSelect: (LogLevel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CompactDropdownButton(
            label = selected.label,
            width = 120.dp,
            expanded = expanded,
            onClick = { expanded = !expanded },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(150.dp).background(PanelAltBackground),
        ) {
            LogLevel.dropdownValues.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label) },
                    onClick = {
                        onSelect(level)
                        expanded = false
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
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = devices.firstOrNull { it.serial == selectedSerial }?.label ?: "No device"
    Box {
        CompactDropdownButton(
            label = selectedLabel,
            width = 280.dp,
            expanded = expanded,
            enabled = devices.isNotEmpty(),
            onClick = { expanded = !expanded },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(320.dp).background(PanelAltBackground),
        ) {
            devices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${device.model}  ${device.serial}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(device.state, color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    onClick = {
                        onSelect(device.serial)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterHistoryDropdown(
    history: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CompactDropdownButton(
            label = if (history.isEmpty()) "History" else "History (${history.size})",
            width = 130.dp,
            expanded = expanded,
            enabled = history.isNotEmpty(),
            onClick = { expanded = !expanded },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(360.dp).background(PanelAltBackground),
        ) {
            history.forEach { filter ->
                DropdownMenuItem(
                    text = {Text(filter, maxLines = 2, overflow = TextOverflow.Ellipsis)},
                    onClick = {
                        onSelect(filter)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DropdownMenuSelectableItem(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (selected) SelectedRowBackground else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun CompactDropdownButton(
    label: String,
    width: Dp,
    expanded: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(width).height(32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AppIcon(
                icon = AppIconSymbol.ChevronDown,
                modifier = Modifier.size(14.dp),
                tint = if (enabled) IconTint else SoftText,
            )
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
    selectedProcessName: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (ProcessDescriptor?) -> Unit,
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
                    selected = selectedProcessName == null,
                    onClick = { onSelect(null) },
                )
            }
            items(processes, key = ProcessDescriptor::pid) { process ->
                SidebarRow(
                    title = process.name,
                    subtitle = "PID ${process.pid}",
                    selected = process.name == selectedProcessName,
                    onClick = { onSelect(process) },
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
        MonoText(
            entry.level.token.toString(),
            modifier = Modifier.width(50.dp),
            color = levelColor,
            fontWeight = FontWeight.Bold
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

            AppIconSymbol.ChevronDown -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.24f, size.height * 0.36f),
                    end = Offset(size.width * 0.5f, size.height * 0.64f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.76f, size.height * 0.36f),
                    end = Offset(size.width * 0.5f, size.height * 0.64f),
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

private fun LazyListState.isAtBottom(): Boolean {
    val layoutInfo = layoutInfo
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    return lastVisibleItem.index >= layoutInfo.totalItemsCount - 1
}

private fun Modifier.clearSelectionOnBackgroundTap(
    focusManager: FocusManager,
    onClearSelection: () -> Unit,
): Modifier =
    clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
    ) {
        focusManager.clearFocus(force = true)
        onClearSelection()
    }

private enum class LogSearchField {
    Any,
    Tag,
    Message,
}

private data class LogSearchToken(
    val field: LogSearchField,
    val negated: Boolean,
    val value: String,
)

private data class CompiledLogSearchToken(
    val field: LogSearchField,
    val negated: Boolean,
    val matches: (String) -> Boolean,
)

private data class LogSearchMatcher(
    val tokens: List<CompiledLogSearchToken>,
) {
    fun matches(entry: LogEntry): Boolean {
        return tokens.all { token ->
            val matched =
                when (token.field) {
                    LogSearchField.Any ->
                        token.matches(entry.tag) ||
                            token.matches(entry.message) ||
                            token.matches(entry.rawLine)

                    LogSearchField.Tag -> token.matches(entry.tag)
                    LogSearchField.Message -> token.matches(entry.message)
                }
            if (token.negated) !matched else matched
        }
    }
}

private fun buildLogSearchMatcher(
    query: String,
    regexEnabled: Boolean,
): Result<LogSearchMatcher?> {
    val normalized = query.trim()
    if (normalized.isEmpty()) {
        return Result.success(null)
    }

    return runCatching {
        val tokens =
            tokenizeSearchQuery(normalized).map { token ->
                val parsed = parseSearchToken(token)
                val matcher: (String) -> Boolean
                if (regexEnabled) {
                    val regex = kotlin.text.Regex(parsed.value, setOf(RegexOption.IGNORE_CASE))
                    matcher = { text: String -> regex.containsMatchIn(text) }
                } else {
                    val needle = parsed.value.lowercase()
                    matcher = { text: String -> text.lowercase().contains(needle) }
                }
                CompiledLogSearchToken(
                    field = parsed.field,
                    negated = parsed.negated,
                    matches = matcher,
                )
            }
        LogSearchMatcher(tokens)
    }
}

private fun tokenizeSearchQuery(query: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    query.forEach { ch ->
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch.isWhitespace() && !inQuotes -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }

            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) {
        tokens += current.toString()
    }
    return tokens
}

private fun parseSearchToken(token: String): LogSearchToken {
    val negated = token.startsWith("-")
    val body = if (negated) token.drop(1) else token
    return when {
        body.startsWith("tag:", ignoreCase = true) ->
            LogSearchToken(
                field = LogSearchField.Tag,
                negated = negated,
                value = body.substringAfter(':'),
            )

        body.startsWith("message:", ignoreCase = true) ->
            LogSearchToken(
                field = LogSearchField.Message,
                negated = negated,
                value = body.substringAfter(':'),
            )

        else ->
            LogSearchToken(
                field = LogSearchField.Any,
                negated = negated,
                value = body,
            )
    }
}
