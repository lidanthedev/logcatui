package me.lidan.logcatui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal data class LogLine(val entryId: Long, val part: Int, val text: String, val level: LogLevel)

internal data class TextPos(val line: Int, val col: Int) : Comparable<TextPos> {
    override fun compareTo(other: TextPos) = compareValuesBy(this, other, TextPos::line, TextPos::col)
}

/** Selection end tied to a log entry, so it survives old entries being trimmed from the front. */
internal data class SelectionEnd(val entryId: Long, val part: Int, val col: Int)

internal fun logLines(logs: List<LogEntry>): List<LogLine> =
    logs.flatMap { entry -> entry.rawLine.split('\n').mapIndexed { i, text -> LogLine(entry.id, i, text, entry.level) } }

internal fun firstLineIndex(lines: List<LogLine>): Map<Long, Int> =
    HashMap<Long, Int>(lines.size).also { map -> lines.forEachIndexed { i, line -> map.putIfAbsent(line.entryId, i) } }

internal fun SelectionEnd.resolve(firstLineOf: Map<Long, Int>): TextPos? = firstLineOf[entryId]?.let { TextPos(it + part, col) }

internal fun selectedText(lines: List<LogLine>, start: TextPos, end: TextPos): String =
    (start.line..end.line).joinToString("\n") { i ->
        val text = lines[i].text
        val from = if (i == start.line) start.col.coerceAtMost(text.length) else 0
        val to = if (i == end.line) end.col.coerceAtMost(text.length) else text.length
        text.substring(from, to.coerceAtLeast(from))
    }

/**
 * Editor-style log output, like Android Studio's logcat: one virtualized monospace document that only lays out
 * visible lines, with its own caret selection spanning any number of lines.
 */
@Composable
internal fun LogTextView(
    logs: List<LogEntry>,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(logs) { logLines(logs) }
    val firstLineOf = remember(lines) { firstLineIndex(lines) }
    var anchor by remember { mutableStateOf<SelectionEnd?>(null) }
    var caret by remember { mutableStateOf<SelectionEnd?>(null) }
    val a = anchor?.resolve(firstLineOf)
    val c = caret?.resolve(firstLineOf)
    val selection = if (a != null && c != null && a != c) minOf(a, c) to maxOf(a, c) else null

    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // shortcut: column hit-testing assumes fixed-width glyphs; tabs and wide CJK/emoji glyphs misalign selection.
    val glyph = remember(density, fontSize) { measurer.measure("M".repeat(100), style).size }
    val charWidth = glyph.width / 100f
    val rowHeight = with(density) { glyph.height.toDp() }
    val padX = 14.dp
    val padXPx = with(density) { padX.toPx() }

    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val currentLines by rememberUpdatedState(lines)
    val currentAutoScroll by rememberUpdatedState(autoScroll)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnAutoScrollChange by rememberUpdatedState(onAutoScrollChange)
    val currentFontSize by rememberUpdatedState(fontSize)
    val currentOnFontSizeChange by rememberUpdatedState(onFontSizeChange)

    fun pause() {
        if (currentAutoScroll) currentOnAutoScrollChange(false)
    }

    fun TextPos.toEnd() = currentLines[line].let { SelectionEnd(it.entryId, it.part, col) }

    fun posAt(offset: Offset): TextPos? {
        val lines = currentLines
        val visible = listState.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { offset.y < it.offset + it.size } ?: visible.lastOrNull() ?: return null
        val line = item.index.coerceIn(lines.indices)
        val col = ((offset.x - padXPx) / charWidth).roundToInt().coerceIn(0, lines[line].text.length)
        return TextPos(line, col)
    }

    LaunchedEffect(lines, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    // Scrolling back to the bottom with nothing selected resumes following, like the old Swing view.
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward && !listState.isScrollInProgress && currentSelection == null }
            .collect { atBottom -> if (atBottom && !currentAutoScroll) currentOnAutoScrollChange(true) }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(AppBackground)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val command = event.isCtrlPressed || event.isMetaPressed
                val page = listState.layoutInfo.viewportSize.height.toFloat()
                val row = glyph.height.toFloat()
                when {
                    command && event.key == Key.C -> selection?.let {
                        Toolkit.getDefaultToolkit().systemClipboard
                            .setContents(StringSelection(selectedText(lines, it.first, it.second)), null)
                    }
                    command && event.key == Key.A && lines.isNotEmpty() -> {
                        pause()
                        anchor = TextPos(0, 0).toEnd()
                        caret = TextPos(lines.lastIndex, lines.last().text.length).toEnd()
                    }
                    command && event.key in setOf(Key.Equals, Key.Plus, Key.NumPadAdd) -> onFontSizeChange(fontSize + 1)
                    command && event.key in setOf(Key.Minus, Key.NumPadSubtract) -> onFontSizeChange(fontSize - 1)
                    command && event.key in setOf(Key.Zero, Key.NumPad0) -> onFontSizeChange(DEFAULT_LOG_FONT_SIZE)
                    event.key == Key.Escape -> { anchor = null; caret = null }
                    event.key == Key.DirectionUp -> { pause(); scope.launch { listState.scrollBy(-row) } }
                    event.key == Key.DirectionDown -> scope.launch { listState.scrollBy(row) }
                    event.key == Key.PageUp -> { pause(); scope.launch { listState.scrollBy(-page) } }
                    event.key == Key.PageDown -> scope.launch { listState.scrollBy(page) }
                    event.key == Key.MoveHome -> { pause(); scope.launch { listState.scrollToItem(0) } }
                    event.key == Key.MoveEnd -> scope.launch { listState.scrollToItem(lines.lastIndex.coerceAtLeast(0)) }
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable()
            .pointerInput(Unit) {
                // Any press or upward wheel inside the output (text or scrollbars) stops following; Ctrl+wheel zooms.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val zoom = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                        when {
                            event.type == PointerEventType.Press -> pause()
                            event.type == PointerEventType.Scroll && zoom -> {
                                val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                                if (dy != 0.0) currentOnFontSizeChange(currentFontSize + if (dy < 0) 1 else -1)
                                event.changes.forEach { it.consume() }
                            }
                            event.type == PointerEventType.Scroll -> if (event.changes.any { it.scrollDelta.y < 0 }) pause()
                        }
                    }
                }
            },
    ) {
        val contentWidth = with(density) { (lines.maxOfOrNull { it.text.length } ?: 0).times(charWidth).plus(2 * padXPx).toDp() }
        val viewportWidth = maxWidth
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxOf(viewportWidth, contentWidth))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                            focusRequester.requestFocus()
                            val start = posAt(down.position) ?: return@awaitEachGesture
                            if (!currentEvent.keyboardModifiers.isShiftPressed || anchor == null) anchor = start.toEnd()
                            caret = start.toEnd()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val y = change.position.y
                                // shortcut: edge auto-scroll only advances while the mouse moves; add a ticker if users want hold-to-scroll.
                                if (y < 0) listState.dispatchRawDelta(y) else if (y > size.height) listState.dispatchRawDelta(y - size.height)
                                posAt(change.position)?.let { caret = it.toEnd() }
                            }
                        }
                    },
            ) {
                items(lines.size, key = { lines[it].entryId to lines[it].part }) { i ->
                    val line = lines[i]
                    BasicText(
                        text = line.text,
                        style = style.copy(color = levelColor(line.level)),
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier
                            .height(rowHeight)
                            .padding(start = padX)
                            .drawBehind {
                                val sel = selection ?: return@drawBehind
                                if (i !in sel.first.line..sel.second.line) return@drawBehind
                                val from = if (i == sel.first.line) sel.first.col else 0
                                // Past line end, highlight one extra cell to show the selected newline.
                                val to = if (i == sel.second.line) sel.second.col else line.text.length + 1
                                drawRect(SelectedRowBackground, Offset(from * charWidth, 0f), Size((to - from) * charWidth, size.height))
                            },
                    )
                }
            }
        }
        // Default thumb is black, invisible on the dark background.
        val scrollbarStyle = LocalScrollbarStyle.current.copy(
            unhoverColor = SoftText.copy(alpha = 0.3f),
            hoverColor = SoftText.copy(alpha = 0.6f),
        )
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight(), style = scrollbarStyle)
        HorizontalScrollbar(rememberScrollbarAdapter(hScroll), Modifier.align(Alignment.BottomStart).fillMaxWidth(), style = scrollbarStyle)
    }
}
