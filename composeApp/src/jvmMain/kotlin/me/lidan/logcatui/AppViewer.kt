package me.lidan.logcatui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogcatViewer(controller: LogcatController) {
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    var deviceSearchQuery by remember { mutableStateOf("") }
    var processSearchQuery by remember { mutableStateOf("") }
    var copyToastMessage by remember { mutableStateOf<String?>(null) }
    var copyToastNonce by remember { mutableIntStateOf(0) }

    val searchMatcherResult by remember(controller.searchQuery, controller.regexEnabled) {
        derivedStateOf {
            buildLogSearchMatcher(controller.searchQuery, controller.regexEnabled)
        }
    }
    val filteredLogs by remember(controller.logs, controller.searchQuery, controller.regexEnabled) {
        derivedStateOf {
            val matcher = searchMatcherResult.getOrNull()
            controller.logs.filter { entry -> matcher?.invoke(entry) ?: true }
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

    LaunchedEffect(copyToastNonce) {
        if (copyToastMessage != null) {
            delay(1400)
            copyToastMessage = null
        }
    }

    LaunchedEffect(controller.detailExpanded, selectedLog?.id, filteredLogs.size) {
        val currentSelectedLog = selectedLog
        if (!controller.detailExpanded || currentSelectedLog == null) {
            return@LaunchedEffect
        }
        val selectedIndex = filteredLogs.indexOfFirst { it.id == currentSelectedLog.id }
        if (selectedIndex >= 0) {
            lazyListState.scrollToItemIfNotVisible(selectedIndex, keepIncrementalDown = false)
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(AppBackground)
                .clearSelectionOnBackgroundTap(focusManager) { controller.selectLog(null) },
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
                    onCopyRawLog = { rawLine ->
                        clipboardManager.setText(AnnotatedString(rawLine))
                        copyToastMessage = "Copied to clipboard"
                        copyToastNonce++
                    },
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

        copyToastMessage?.let { message ->
            ToastMessage(
                message = message,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

internal fun LazyListState.isAtBottom(): Boolean {
    val layoutInfo = layoutInfo
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    return lastVisibleItem.index >= layoutInfo.totalItemsCount - 1
}

internal suspend fun LazyListState.scrollToItemIfNotVisible(
    index: Int,
    keepIncrementalDown: Boolean = true,
) {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        scrollToItem(index)
        return
    }

    val firstVisible = visibleItems.first().index
    val lastVisible = visibleItems.last().index
    if (index < firstVisible) {
        scrollToItem(index)
        return
    }

    if (index > lastVisible) {
        if (keepIncrementalDown) {
            val nextFirstVisible = (firstVisible + 1).coerceAtMost(index)
            scrollToItem(nextFirstVisible, firstVisibleItemScrollOffset)
        } else {
            scrollToItem(index)
        }
    }
}

internal fun Modifier.clearSelectionOnBackgroundTap(
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
