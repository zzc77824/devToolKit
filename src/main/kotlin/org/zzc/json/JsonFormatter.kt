package org.zzc.json

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
import com.fasterxml.jackson.databind.node.NullNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider

/** Machine-readable reason why a JSON operation failed. */
enum class JsonError {
    /** The input is not valid JSON. */
    INVALID_JSON,

    /** The JSONPath expression is malformed. */
    INVALID_PATH,

    /** The JSONPath expression is valid but matches nothing. */
    PATH_NOT_FOUND,
}

/** Thrown when parsing, formatting or querying JSON fails. */
class JsonException(
    val error: JsonError,
    val line: Int? = null,
    val column: Int? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Result of a JSONPath query: the pretty-printed matches and how many values were returned. */
data class JsonQueryResult(
    val json: String,
    val matchCount: Int,
)

/**
 * Parses, pretty-prints, minifies and queries JSON.
 *
 * Formatting keeps the original key order and writes 2/4 spaces of indentation in the common
 * `"key": value` style. JSONPath is evaluated with Jayway JSONPath, so the full JSONPath syntax
 * (including filters and recursive descent) is supported.
 */
class JsonFormatter {

    private val mapper = ObjectMapper()
        // Keep numeric literals exactly as written (1.10 stays 1.10, big integers stay exact).
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)

    private val jsonPathConfiguration: Configuration = Configuration.builder()
        .jsonProvider(JacksonJsonProvider(mapper))
        .mappingProvider(JacksonMappingProvider(mapper))
        .build()

    fun format(text: String, indentSize: Int = DEFAULT_INDENT): String {
        require(indentSize in 0..8) { "indentSize must be between 0 and 8: $indentSize" }
        return writePretty(parse(text), indentSize)
    }

    fun minify(text: String): String = mapper.writeValueAsString(parse(text))

    fun query(text: String, path: String, indentSize: Int = DEFAULT_INDENT): JsonQueryResult {
        require(indentSize in 0..8) { "indentSize must be between 0 and 8: $indentSize" }
        if (path.isBlank()) {
            throw JsonException(JsonError.INVALID_PATH, message = "JSONPath is empty")
        }

        // Validate with Jackson first so syntax errors carry line/column information.
        parse(text)

        val definite = try {
            JsonPath.isPathDefinite(path)
        } catch (e: InvalidPathException) {
            throw JsonException(JsonError.INVALID_PATH, message = e.message ?: "Invalid JSONPath", cause = e)
        }

        val result: Any? = try {
            JsonPath.using(jsonPathConfiguration).parse(text).read<Any>(path)
        } catch (e: PathNotFoundException) {
            throw JsonException(JsonError.PATH_NOT_FOUND, message = e.message ?: "No match", cause = e)
        } catch (e: InvalidPathException) {
            throw JsonException(JsonError.INVALID_PATH, message = e.message ?: "Invalid JSONPath", cause = e)
        }

        // Explicit type: valueToTree is generic and would otherwise infer NullNode here.
        val node: JsonNode = if (result == null) NullNode.instance else mapper.valueToTree(result)
        val matchCount = when {
            definite -> 1
            result is Collection<*> -> result.size
            else -> 1
        }
        return JsonQueryResult(writePretty(node, indentSize), matchCount)
    }

    private fun parse(text: String): JsonNode {
        if (text.isBlank()) {
            throw JsonException(JsonError.INVALID_JSON, message = "JSON is empty")
        }
        val node = try {
            mapper.readTree(text)
        } catch (e: JsonProcessingException) {
            val location = e.location
            throw JsonException(
                error = JsonError.INVALID_JSON,
                line = location?.lineNr?.takeIf { it > 0 },
                column = location?.columnNr?.takeIf { it > 0 },
                message = e.originalMessage,
                cause = e,
            )
        }
        return node ?: throw JsonException(JsonError.INVALID_JSON, message = "JSON is empty")
    }

    private fun writePretty(node: JsonNode, indentSize: Int): String {
        val builder = StringBuilder()
        writeNode(node, indentSize, 0, builder)
        return builder.toString()
    }

    private fun writeNode(node: JsonNode, indentSize: Int, level: Int, out: StringBuilder) {
        when {
            node.isObject -> writeObject(node, indentSize, level, out)
            node.isArray -> writeArray(node, indentSize, level, out)
            node.isTextual -> out.append(mapper.writeValueAsString(node.textValue()))
            node.isBinary -> out.append(mapper.writeValueAsString(node.binaryValue()))
            else -> out.append(mapper.writeValueAsString(node))
        }
    }

    private fun writeObject(node: JsonNode, indentSize: Int, level: Int, out: StringBuilder) {
        if (node.size() == 0) {
            out.append("{}")
            return
        }
        out.append("{\n")
        var first = true
        for ((name, value) in node.properties()) {
            if (!first) out.append(",\n")
            first = false
            appendIndent(out, indentSize, level + 1)
            out.append(mapper.writeValueAsString(name)).append(": ")
            writeNode(value, indentSize, level + 1, out)
        }
        out.append('\n')
        appendIndent(out, indentSize, level)
        out.append('}')
    }

    private fun writeArray(node: JsonNode, indentSize: Int, level: Int, out: StringBuilder) {
        if (node.size() == 0) {
            out.append("[]")
            return
        }
        out.append("[\n")
        for (index in 0 until node.size()) {
            if (index > 0) out.append(",\n")
            appendIndent(out, indentSize, level + 1)
            writeNode(node.get(index), indentSize, level + 1, out)
        }
        out.append('\n')
        appendIndent(out, indentSize, level)
        out.append(']')
    }

    private fun appendIndent(out: StringBuilder, indentSize: Int, level: Int) {
        if (indentSize > 0) {
            out.append(" ".repeat(indentSize * level))
        }
    }

    companion object {
        const val DEFAULT_INDENT = 2
    }
}
