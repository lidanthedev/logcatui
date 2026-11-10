package me.lidan.logcatui

import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.text.DefaultEditorKit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogTextViewLayoutTest {
    @Test
    fun `follow scrolls to bottom and a manual scroll keeps its position on append`() {
        lateinit var view: LogTextView
        var following = true
        val logs = (1L..100L).map { id ->
            val line = "03-25 12:00:00.000  42  43 I Test: line $id"
            LogEntry(id, "03-25 12:00:00.000", 42, 43, LogLevel.Info, "Test", "line $id", line)
        }
        SwingUtilities.invokeAndWait {
            view = LogTextView { following = it }
            view.setSize(640, 160)
            view.updateLogs(logs, true)
            view.doLayout()
            view.viewport.doLayout()
            view.doLayout()
        }
        SwingUtilities.invokeAndWait {
            val bar = view.verticalScrollBar
            assertTrue(bar.maximum > bar.visibleAmount)
            assertEquals(bar.maximum - bar.visibleAmount, bar.value)
            bar.valueIsAdjusting = true
            bar.value = 120
            bar.valueIsAdjusting = false
            assertFalse(following)
            view.updateLogs(logs + logs.last().copy(id = 101, rawLine = "new log line"), following)
            view.doLayout()
            view.viewport.doLayout()
            view.doLayout()
        }
        SwingUtilities.invokeAndWait {
            assertEquals(120, view.verticalScrollBar.value)
        }
    }

    @Test
    fun `scrollbar arrow suspends following`() {
        lateinit var view: LogTextView
        var following = true
        SwingUtilities.invokeAndWait {
            view = LogTextView { following = it }
            view.setSize(640, 160)
            val logs = (1L..100L).map { id ->
                LogEntry(id, "--", 42, 43, LogLevel.Info, "Test", "line $id", "line $id")
            }
            view.updateLogs(logs, true)
            view.doLayout()
            view.viewport.doLayout()
            view.doLayout()
        }
        SwingUtilities.invokeAndWait {
            val upButton = view.verticalScrollBar.components.filterIsInstance<JButton>().minBy { it.y }
            upButton.dispatchEvent(MouseEvent(upButton, MouseEvent.MOUSE_PRESSED, 0,
                MouseEvent.BUTTON1_DOWN_MASK, 4, 4, 1, false, MouseEvent.BUTTON1))
            upButton.dispatchEvent(MouseEvent(upButton, MouseEvent.MOUSE_RELEASED, 1,
                0, 4, 4, 1, false, MouseEvent.BUTTON1))
        }
        SwingUtilities.invokeAndWait {
            assertFalse(following)
        }
    }

    @Test
    fun `keyboard end resumes following after scrolling up`() {
        lateinit var view: LogTextView
        var following = false
        SwingUtilities.invokeAndWait {
            view = LogTextView { following = it }
            view.setSize(640, 160)
            val logs = (1L..100L).map { id ->
                LogEntry(id, "--", 42, 43, LogLevel.Info, "Test", "line $id", "line $id")
            }
            view.updateLogs(logs, false)
            view.doLayout()
            view.viewport.doLayout()
            view.doLayout()
            val event = KeyEvent(view.output, KeyEvent.KEY_PRESSED, 0, KeyEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED)
            view.output.keyListeners.forEach { it.keyPressed(event) }
            view.output.actionMap.get(DefaultEditorKit.endAction).actionPerformed(
                ActionEvent(view.output, ActionEvent.ACTION_PERFORMED, DefaultEditorKit.endAction),
            )
            val released = KeyEvent(view.output, KeyEvent.KEY_RELEASED, 1, KeyEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED)
            view.output.keyListeners.forEach { it.keyReleased(released) }
        }
        SwingUtilities.invokeAndWait {
            assertTrue(following, "value=${view.verticalScrollBar.value} extent=${view.verticalScrollBar.visibleAmount} max=${view.verticalScrollBar.maximum} caret=${view.output.caretPosition}")
        }
    }

}
