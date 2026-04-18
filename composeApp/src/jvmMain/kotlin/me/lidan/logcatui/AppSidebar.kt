package me.lidan.logcatui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun Sidebar(
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
internal fun DeviceList(
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
internal fun ProcessList(
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
