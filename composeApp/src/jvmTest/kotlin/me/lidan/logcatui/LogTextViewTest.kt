package me.lidan.logcatui

import androidx.compose.ui.graphics.toArgb
import java.awt.Color
import java.awt.event.MouseEvent
import java.time.Instant
import javax.swing.SwingUtilities
import javax.swing.text.StyleConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogTextViewTest {
    private fun entry(id: Long, level: LogLevel = LogLevel.Info, rawLine: String = "line $id") = LogEntry(
        id = id, timestamp = "03-25 12:00:00.000", pid = 42, tid = 43,
        level = level, tag = "Test", message = rawLine, rawLine = rawLine, receivedAt = Instant.EPOCH,
    )

    @Test
    fun `text is read only colored and selectable across entries`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        val logs = listOf(entry(1, LogLevel.Warn), entry(2, LogLevel.Error, "full long message\ncontinuation"))
        view.updateLogs(logs, false)
        assertFalse(view.output.isEditable)
        view.output.select(0, view.output.document.length)
        assertEquals("line 1\nfull long message\ncontinuation\n", view.output.selectedText)
        for ((offset, level) in listOf(0 to LogLevel.Warn, 7 to LogLevel.Error)) {
            assertEquals(
                Color(levelColor(level).toArgb(), true),
                StyleConstants.getForeground(view.output.styledDocument.getCharacterElement(offset).attributes),
            )
        }
    }

    @Test
    fun `appending retains a backwards selection`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        view.updateLogs(listOf(entry(1), entry(2)), false)
        view.output.caret.setDot(13)
        view.output.caret.moveDot(2)
        view.updateLogs(listOf(entry(1), entry(2), entry(3)), false)
        assertEquals(13, view.output.caret.mark)
        assertEquals(2, view.output.caret.dot)
        assertEquals("ne 1\nline 2", view.output.selectedText)
        assertEquals("line 1\nline 2\nline 3\n", view.output.text)
    }

    @Test
    fun `trimming the head shifts the retained selection`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        view.updateLogs(listOf(entry(1), entry(2), entry(3)), false)
        view.output.select(7, 20)
        view.updateLogs(listOf(entry(2), entry(3), entry(4)), false)
        assertEquals("line 2\nline 3\nline 4\n", view.output.text)
        assertEquals("line 2\nline 3", view.output.selectedText)
    }

    @Test
    fun `filter changes rebuild the document without stale lines`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        val logs = listOf(entry(1), entry(2), entry(3))
        view.updateLogs(logs, false)
        view.updateLogs(listOf(logs[0], logs[2]), false)
        assertEquals("line 1\nline 3\n", view.output.text)
        view.updateLogs(listOf(entry(10)), false)
        assertEquals("line 10\n", view.output.text)
        view.updateLogs(emptyList(), false)
        assertEquals("", view.output.text)
    }

    @Test
    fun `changing a filter with the same endpoints replaces the middle entries`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        view.updateLogs(listOf(entry(1), entry(2), entry(4)), false)
        view.updateLogs(listOf(entry(1), entry(3), entry(4)), false)
        assertEquals("line 1\nline 3\nline 4\n", view.output.text)
    }

    @Test
    fun `same entries keep the existing document and selection`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        val logs = listOf(entry(1), entry(2))
        view.updateLogs(logs, false)
        view.output.select(2, 10)
        view.updateLogs(logs.toList(), false)
        assertEquals("ne 1\nlin", view.output.selectedText)
    }

    @Test
    fun `mouse drag selects multiple lines and disables following`() = SwingUtilities.invokeAndWait {
        var following = true
        val view = LogTextView { following = it }
        view.updateLogs(listOf(entry(1), entry(2), entry(3)), false)
        val output = view.output
        output.setSize(640, 320)
        val start = output.modelToView2D(2)
        val end = output.modelToView2D(19)
        output.dispatchEvent(MouseEvent(output, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK,
            start.x.toInt(), start.centerY.toInt(), 1, false, MouseEvent.BUTTON1))
        output.dispatchEvent(MouseEvent(output, MouseEvent.MOUSE_DRAGGED, 1, MouseEvent.BUTTON1_DOWN_MASK,
            end.x.toInt(), end.centerY.toInt(), 0, false, MouseEvent.NOBUTTON))
        output.dispatchEvent(MouseEvent(output, MouseEvent.MOUSE_RELEASED, 2, 0,
            end.x.toInt(), end.centerY.toInt(), 1, false, MouseEvent.BUTTON1))
        assertFalse(following)
        assertEquals("ne 1\nline 2\nline ", output.selectedText)
    }

    @Test
    fun `full retained buffer supports selection and rolling updates`() = SwingUtilities.invokeAndWait {
        val view = LogTextView {}
        val logs = (1L..50_000).map { entry(it) }
        view.updateLogs(logs, false)
        view.output.select(0, view.output.document.length)
        assertEquals(logs.joinToString("") { it.rawLine + "\n" }, view.output.selectedText)
        view.updateLogs(logs.drop(1) + entry(50_001), false)
        assertEquals("line 2\n", view.output.document.getText(0, 7))
        assertEquals("line 50001\n", view.output.document.getText(view.output.document.length - 11, 11))
    }
}
