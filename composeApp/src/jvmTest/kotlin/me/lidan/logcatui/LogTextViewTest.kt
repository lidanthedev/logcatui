package me.lidan.logcatui

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogTextViewTest {
    private fun entry(id: Long, rawLine: String = "line $id") = LogEntry(
        id = id, timestamp = "03-25 12:00:00.000", pid = 42, tid = 43,
        level = LogLevel.Info, tag = "Test", message = rawLine, rawLine = rawLine, receivedAt = Instant.EPOCH,
    )

    @Test
    fun `multi line entries become separate lines`() {
        val lines = logLines(listOf(entry(1), entry(2, "full long message\ncontinuation")))
        assertEquals(listOf("line 1", "full long message", "continuation"), lines.map { it.text })
        assertEquals(listOf(0, 0, 1), lines.map { it.part })
    }

    @Test
    fun `selection text spans lines and clamps columns`() {
        val lines = logLines(listOf(entry(1), entry(2, "full long message\ncontinuation")))
        assertEquals("ne 1\nfull long message\ncont", selectedText(lines, TextPos(0, 2), TextPos(2, 4)))
        assertEquals("message", selectedText(lines, TextPos(1, 10), TextPos(1, 99)))
    }

    @Test
    fun `double click range picks word whitespace or single symbol`() {
        val text = "I ActivityManager: Start proc_42  {cmp=x}"
        assertEquals("ActivityManager", text.substring(wordRange(text, 5)))
        assertEquals("proc_42", text.substring(wordRange(text, 30)))
        assertEquals(":", text.substring(wordRange(text, 17)))
        assertEquals("  ", text.substring(wordRange(text, 32)))
        assertEquals("}", text.substring(wordRange(text, 999)))
        assertEquals(IntRange.EMPTY, wordRange("", 3))
    }

    @Test
    fun `selection follows its entry when older entries are trimmed`() {
        val end = SelectionEnd(entryId = 3, part = 1, col = 2)
        val beforeLines = logLines(listOf(entry(1), entry(2), entry(3, "a\nbc")))
        assertEquals(TextPos(3, 2), end.resolve(beforeLines, firstLineIndex(beforeLines)))
        val afterLines = logLines(listOf(entry(3, "a\nbc"), entry(4)))
        val after = firstLineIndex(afterLines)
        assertEquals(TextPos(1, 2), end.resolve(afterLines, after))
        // A trimmed endpoint clamps to the start; an unknown newer entry (list replaced) drops the selection.
        assertEquals(TextPos(0, 0), SelectionEnd(1, 0, 5).resolve(afterLines, after))
        assertNull(SelectionEnd(9, 0, 0).resolve(afterLines, after))
    }
}
