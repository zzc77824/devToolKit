package org.zzc.json

import com.jayway.jsonpath.InvalidPathException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonFormatterTest {

    private val formatter = JsonFormatter()

    private val sample = """
        {"store":{"book":[{"title":"A","price":10},{"title":"B","price":20}]}}
    """.trimIndent()

    @Test
    fun `format uses two spaces by default`() {
        assertEquals(
            """
            {
              "a": 1,
              "b": [
                1,
                2
              ]
            }
            """.trimIndent(),
            formatter.format("""{"a":1,"b":[1,2]}"""),
        )
    }

    @Test
    fun `format supports four spaces`() {
        assertEquals(
            """
            {
                "a": 1
            }
            """.trimIndent(),
            formatter.format("""{"a":1}""", indentSize = 4),
        )
    }

    @Test
    fun `format keeps empty containers compact`() {
        assertEquals(
            """
            {
              "a": {},
              "b": []
            }
            """.trimIndent(),
            formatter.format("""{"a":{},"b":[]}"""),
        )
    }

    @Test
    fun `format preserves numeric literals`() {
        assertEquals(
            """
            {
              "n": 1.10,
              "big": 123456789012345678901234567890
            }
            """.trimIndent(),
            formatter.format("""{"n":1.10,"big":123456789012345678901234567890}"""),
        )
    }

    @Test
    fun `format keeps key order and unicode`() {
        val formatted = formatter.format("""{"z":1,"a":"张三"}""")

        assertTrue(formatted.indexOf("\"z\"") < formatted.indexOf("\"a\""))
        assertTrue(formatted.contains("\"张三\""))
    }

    @Test
    fun `minify removes insignificant whitespace`() {
        assertEquals(
            """{"a":1,"b":[1,2]}""",
            formatter.minify(" { \"a\" : 1, \"b\" : [ 1, 2 ] } "),
        )
    }

    @Test
    fun `invalid json reports line`() {
        try {
            formatter.format("{\n  \"a\": ,\n}")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            assertEquals(JsonError.INVALID_JSON, e.error)
            assertEquals(2, e.line)
            assertTrue(e.column != null)
        }
    }

    @Test
    fun `blank input fails`() {
        try {
            formatter.minify("   ")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            assertEquals(JsonError.INVALID_JSON, e.error)
        }
    }

    @Test
    fun `jsonpath definite match returns single value`() {
        val result = formatter.query(sample, "$.store.book[0].title")

        assertEquals("\"A\"", result.json)
        assertEquals(1, result.matchCount)
    }

    @Test
    fun `jsonpath wildcard returns all matches`() {
        val result = formatter.query(sample, "$.store.book[*].title")

        assertEquals(
            """
            [
              "A",
              "B"
            ]
            """.trimIndent(),
            result.json,
        )
        assertEquals(2, result.matchCount)
    }

    @Test
    fun `jsonpath filter is supported`() {
        val result = formatter.query(sample, "$.store.book[?(@.price > 10)].title")

        assertEquals(
            """
            [
              "B"
            ]
            """.trimIndent(),
            result.json,
        )
        assertEquals(1, result.matchCount)
    }

    @Test
    fun `jsonpath query keeps numeric precision`() {
        assertEquals("1.10", formatter.query("""{"n":1.10}""", "$.n").json)
    }

    @Test
    fun `jsonpath missing path fails`() {
        try {
            formatter.query(sample, "$.store.missing")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            assertEquals(JsonError.PATH_NOT_FOUND, e.error)
        }
    }

    @Test
    fun `jsonpath invalid expression fails`() {
        try {
            formatter.query(sample, "$[?(@.price >)]")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            assertEquals(JsonError.INVALID_PATH, e.error)
        } catch (e: InvalidPathException) {
            fail("InvalidPathException should be wrapped into JsonException: ${e.message}")
        }
    }

    @Test
    fun `blank jsonpath fails`() {
        try {
            formatter.query(sample, "  ")
            fail("Expected JsonException")
        } catch (e: JsonException) {
            assertEquals(JsonError.INVALID_PATH, e.error)
        }
    }
}
