package me.lidan.logcatui

import kotlin.text.RegexOption

private enum class LogSearchField {
    Any,
    Tag,
    Message,
}

private data class LogSearchToken(
    val field: LogSearchField,
    val negated: Boolean,
    val value: String,
)

private data class CompiledLogSearchToken(
    val field: LogSearchField,
    val negated: Boolean,
    val matches: (String) -> Boolean,
)

private class LogSearchMatcher(
    private val tokens: List<CompiledLogSearchToken>,
) {
    fun matches(entry: LogEntry): Boolean {
        return tokens.all { token ->
            val matched =
                when (token.field) {
                    LogSearchField.Any ->
                        token.matches(entry.tag) ||
                            token.matches(entry.message) ||
                            token.matches(entry.rawLine)

                    LogSearchField.Tag -> token.matches(entry.tag)
                    LogSearchField.Message -> token.matches(entry.message)
                }
            if (token.negated) !matched else matched
        }
    }
}

internal fun buildLogSearchMatcher(
    query: String,
    regexEnabled: Boolean,
): Result<((LogEntry) -> Boolean)?> {
    val normalized = query.trim()
    if (normalized.isEmpty()) {
        return Result.success(null)
    }

    return runCatching {
        val tokens =
            tokenizeSearchQuery(normalized).map { token ->
                val parsed = parseSearchToken(token)
                val matcher: (String) -> Boolean
                if (regexEnabled) {
                    val regex = Regex(parsed.value, setOf(RegexOption.IGNORE_CASE))
                    matcher = { text: String -> regex.containsMatchIn(text) }
                } else {
                    val needle = parsed.value.lowercase()
                    matcher = { text: String -> text.lowercase().contains(needle) }
                }
                CompiledLogSearchToken(
                    field = parsed.field,
                    negated = parsed.negated,
                    matches = matcher,
                )
            }
        val matcher = LogSearchMatcher(tokens)
        val predicate: (LogEntry) -> Boolean = { entry -> matcher.matches(entry) }
        predicate
    }
}

private fun tokenizeSearchQuery(query: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    query.forEach { ch ->
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch.isWhitespace() && !inQuotes -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }

            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) {
        tokens += current.toString()
    }
    return tokens
}

private fun parseSearchToken(token: String): LogSearchToken {
    val negated = token.startsWith("-")
    val body = if (negated) token.drop(1) else token
    return when {
        body.startsWith("tag:", ignoreCase = true) ->
            LogSearchToken(
                field = LogSearchField.Tag,
                negated = negated,
                value = body.substringAfter(':'),
            )

        body.startsWith("message:", ignoreCase = true) ->
            LogSearchToken(
                field = LogSearchField.Message,
                negated = negated,
                value = body.substringAfter(':'),
            )

        else ->
            LogSearchToken(
                field = LogSearchField.Any,
                negated = negated,
                value = body,
            )
    }
}
