package org.zzc.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFoldRangesTest {

    @Test
    fun `folds nested multiline objects`() {
        val text = """
            {
              "a": {
                "b": 1
              }
            }
        """.trimIndent()

        val ranges = JsonFoldRanges.compute(text)

        assertEquals(2, ranges.size)
        assertTrue(ranges.all { it.placeholder == "{…}" })
    }

    @Test
    fun `folds arrays and objects independently`() {
        val text = """
            {
              "a": [
                1,
                2
              ]
            }
        """.trimIndent()

        val ranges = JsonFoldRanges.compute(text)

        assertEquals(2, ranges.size)
        assertEquals(1, ranges.count { it.placeholder == "[…]" })
        assertEquals(1, ranges.count { it.placeholder == "{…}" })
    }

    @Test
    fun `ignores braces inside strings`() {
        val text = """{"s":"{ not a brace }","n":1}"""

        val ranges = JsonFoldRanges.compute(text, minContentLength = 1)

        assertEquals(1, ranges.size)
    }

    @Test
    fun `skips empty containers`() {
        assertEquals(0, JsonFoldRanges.compute("{}").size)
        assertEquals(0, JsonFoldRanges.compute("[]").size)
    }

    @Test
    fun `skips short inline containers by default`() {
        assertEquals(0, JsonFoldRanges.compute("""{"a":1}""").size)
    }

    @Test
    fun `range offsets cover content without braces`() {
        val text = "{\n  \"a\": 1\n}"
        val range = JsonFoldRanges.compute(text).single()

        assertEquals('{', text[range.startOffset - 1])
        assertEquals('}', text[range.endOffset])
        assertTrue(range.endOffset > range.startOffset)
    }
}
