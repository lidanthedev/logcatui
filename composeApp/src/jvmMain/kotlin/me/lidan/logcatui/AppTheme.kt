package me.lidan.logcatui

import androidx.compose.ui.graphics.Color

internal val AppBackground = Color(0xFF1E1F22)
internal val AppBorder = Color(0xFF393B40)
internal val ToolbarBackground = Color(0xFF25272C)
internal val PanelBackground = Color(0xFF23252A)
internal val PanelAltBackground = Color(0xFF2A2D33)
internal val SelectedRowBackground = Color(0xFF314158)
internal val SoftText = Color(0xFFAEB4BE)
internal val Success = Color(0xFF74C365)
internal val Warning = Color(0xFFF5C451)
internal val ErrorRed = Color(0xFFFF6B68)
internal val VerboseGray = Color(0xFF8A8F99)
internal val InfoGreen = Color(0xFF7FD37F)
internal val IconTint = Color(0xFFC7CDD7)
internal val TextFieldBackground = Color(0xFF2B2D31)
internal val TextFieldFocusedBorder = Color(0xFF4B6EAF)

internal enum class AppIconSymbol {
    Clear,
    Refresh,
    Search,
    Close,
    ScrollToEnd,
    ChevronDown,
    Pause,
    Resume,
}

internal fun levelColor(level: LogLevel): Color =
    when (level) {
        LogLevel.Verbose -> VerboseGray
        LogLevel.Debug -> SoftText
        LogLevel.Info -> Success
        LogLevel.Warn -> Warning
        LogLevel.Error, LogLevel.Assert -> ErrorRed
    }
