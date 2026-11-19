package org.zzc.json

/** A foldable region inside JSON text: [startOffset, endOffset) covers the container's content. */
data class JsonFoldRange(
    val startOffset: Int,
    val endOffset: Int,
    val placeholder: String,
)

/**
 * Computes brace/bracket based folding ranges for JSON text.
 *
 * Only containers with multi-line or reasonably large content are folded, so tiny inline objects
 * such as `{"a": 1}` do not add noise. Braces inside strings are ignored.
 */
object JsonFoldRanges {

    fun compute(text: CharSequence, minContentLength: Int = 20): List<JsonFoldRange> {
        val ranges = mutableListOf<JsonFoldRange>()
        val stack = ArrayDeque<Int>()
        var index = 0
        var inString = false
        var escaped = false

        while (index < text.length) {
            val c = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                index++
                continue
            }

            when (c) {
                '"' -> inString = true
                '{', '[' -> stack.addLast(index)
                '}', ']' -> {
                    val open = if (stack.isEmpty()) null else stack.removeLast()
                    if (open != null) {
                        val openChar = text[open]
                        val matches = (openChar == '{' && c == '}') || (openChar == '[' && c == ']')
                        if (matches) {
                            val contentStart = open + 1
                            val contentEnd = index
                            if (contentEnd > contentStart) {
                                val content = text.subSequence(contentStart, contentEnd)
                                if (content.contains('\n') || content.length >= minContentLength) {
                                    ranges += JsonFoldRange(
                                        startOffset = contentStart,
                                        endOffset = contentEnd,
                                        placeholder = if (openChar == '{') "{…}" else "[…]",
                                    )
                                }
                            }
                        }
                    }
                }
            }
            index++
        }
        return ranges
    }
}
