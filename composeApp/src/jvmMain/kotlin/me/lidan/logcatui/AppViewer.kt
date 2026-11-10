package me.lidan.logcatui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LogcatViewer(controller: LogcatController) {
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

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Toolbar(
            controller = controller,
            regexError = searchMatcherResult.exceptionOrNull()?.message,
        )
        HorizontalDivider(color = AppBorder)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                title = "Devices",
                onRefresh = controller::refreshDevices,
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
                onRefresh = controller::refreshProcesses,
                refreshEnabled = controller.selectedDeviceSerial != null,
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
                modifier = Modifier.weight(1f),
            )
        }
    }
}
