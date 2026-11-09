package me.lidan.logcatui

import androidx.compose.ui.graphics.toArgb
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/** One document keeps selection and copying continuous across the entire retained log buffer. */
internal class LogTextView(private val onAutoScrollChange: (Boolean) -> Unit) : JScrollPane() {
    internal val output = JTextPane().apply {
        isEditable = false
        background = Color(AppBackground.toArgb(), true)
        foreground = Color(SoftText.toArgb(), true)
        caretColor = foreground
        selectionColor = Color(SelectedRowBackground.toArgb(), true)
        selectedTextColor = Color.WHITE
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        margin = Insets(8, 14, 8, 14)
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
        accessibleContext.accessibleName = "Log output"
    }
    private val styles = LogLevel.entries.associateWith { level ->
        SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, Color(levelColor(level).toArgb(), true))
        }
    }
    private var renderedLogs: List<LogEntry> = emptyList()
    private var following = true

    init {
        setViewportView(output)
        border = null
        viewport.background = output.background
        output.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                following = false
                onAutoScrollChange(false)
            }
        })
        output.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode in setOf(KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT,
                        KeyEvent.VK_RIGHT, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_HOME,
                        KeyEvent.VK_END, KeyEvent.VK_A)) {
                    following = false
                    onAutoScrollChange(false)
                }
            }
        })
        verticalScrollBar.addAdjustmentListener { event ->
            if (event.valueIsAdjusting) {
                updateFollowingFromScroll()
            }
        }
        verticalScrollBar.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(event: MouseEvent) {
                SwingUtilities.invokeLater(::updateFollowingFromScroll)
            }
        })
        addMouseWheelListener {
            SwingUtilities.invokeLater(::updateFollowingFromScroll)
        }
    }

    private fun updateFollowingFromScroll() {
        val bar = verticalScrollBar
        following = bar.value + bar.visibleAmount >= bar.maximum && output.selectionStart == output.selectionEnd
        onAutoScrollChange(following)
    }

    fun updateLogs(logs: List<LogEntry>, autoScroll: Boolean) {
        following = autoScroll
        if (logs != renderedLogs) {
            val document = output.styledDocument
            val oldDot = output.caret.dot
            val oldMark = output.caret.mark
            val firstRetained = logs.firstOrNull()?.let { first -> renderedLogs.indexOfFirst { it.id == first.id } } ?: -1
            val retainedCount = if (firstRetained >= 0) renderedLogs.size - firstRetained else 0
            val canAppend = firstRetained >= 0 && retainedCount <= logs.size &&
                renderedLogs.subList(firstRetained, renderedLogs.size) == logs.take(retainedCount)
            val removedCharacters = if (canAppend) {
                renderedLogs.take(firstRetained).sumOf { it.rawLine.length + 1 }
            } else {
                document.length
            }
            document.remove(0, removedCharacters)
            for (entry in logs.drop(if (canAppend) retainedCount else 0)) {
                document.insertString(document.length, entry.rawLine + "\n", styles.getValue(entry.level))
            }
            val mark = if (canAppend) (oldMark - removedCharacters).coerceAtLeast(0) else 0
            val dot = if (canAppend) (oldDot - removedCharacters).coerceAtLeast(0) else 0
            if (output.caret.mark != mark || output.caret.dot != dot) {
                output.caret.setDot(mark)
                output.caret.moveDot(dot)
            }
            renderedLogs = logs.toList()
        }
        if (autoScroll) {
            // Layout updates the scrollbar range after the document has changed.
            SwingUtilities.invokeLater {
                if (following) {
                    verticalScrollBar.value = verticalScrollBar.maximum
                }
            }
        }
    }
}
