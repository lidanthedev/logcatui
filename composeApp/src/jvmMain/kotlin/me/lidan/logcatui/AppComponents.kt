package me.lidan.logcatui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun Toolbar(
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
        OutlinedButton(onClick = controller::togglePause) {
            val icon = if (controller.isPaused) AppIconSymbol.Resume else AppIconSymbol.Pause
            val label = if (controller.isPaused) "Resume" else "Pause"
            ToolbarButtonContent(icon, label)
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
internal fun ToolbarButtonContent(icon: AppIconSymbol, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon = icon, modifier = Modifier.width(16.dp).height(16.dp))
        Text(label)
    }
}

@Composable
internal fun SearchField(
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
        icon = { AppIcon(AppIconSymbol.Search, modifier = Modifier.width(14.dp).height(14.dp)) },
    )
}

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    readOnly: Boolean = false,
    onSubmit: () -> Unit = {},
    icon: @Composable RowScope.() -> Unit = {},
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
                    text = { Text(filter, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
                modifier = Modifier.width(14.dp).height(14.dp),
                tint = if (enabled) IconTint else SoftText,
            )
        }
    }
}
