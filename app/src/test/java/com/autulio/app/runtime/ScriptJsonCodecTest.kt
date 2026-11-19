package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptJsonCodecTest {
    @Test
    fun parsesAndSerializesNestedUnicodeJson() {
        val source = """{"tên":"AUTU-LIO","items":[1,true,null,{"ký_tự":"Tiếng Việt \uD83D\uDE80"}]}"""
        val parsed = ScriptJsonCodec.parse(source) as Map<*, *>
        assertEquals("AUTU-LIO", parsed["tên"])
        val items = parsed["items"] as List<*>
        assertEquals(1.0, items[0])
        assertEquals(true, items[1])
        assertNull(items[2])
        val nested = items[3] as Map<*, *>
        assertEquals("Tiếng Việt 🚀", nested["ký_tự"])

        val serialized = ScriptJsonCodec.stringify(parsed)
        val reparsed = ScriptJsonCodec.parse(serialized) as Map<*, *>
        assertEquals(parsed, reparsed)
    }

    @Test
    fun parsesPrimitiveAndEmptyJsonValues() {
        assertNull(ScriptJsonCodec.parse("null"))
        assertEquals(true, ScriptJsonCodec.parse("true"))
        assertEquals(false, ScriptJsonCodec.parse("false"))
        assertEquals(0.0, ScriptJsonCodec.parse("0"))
        assertEquals(emptyList<Any?>(), ScriptJsonCodec.parse("[]"))
        assertEquals(emptyMap<String, Any?>(), ScriptJsonCodec.parse("{}"))
    }

    @Test
    fun rejectsMalformedDuplicateAndTrailingJson() {
        val malformed = listOf(
            "{bad}",
            """{"a":1,"a":2}""",
            "[1,]",
            "01",
            """{"a":}""",
            """{"a":"control
char"}""",
            "true false"
        )
        malformed.forEach { source ->
            val result = runCatching { ScriptJsonCodec.parse(source) }
            assertFalse("Expected invalid JSON to fail: $source", result.isSuccess)
        }
    }

    @Test
    fun enforcesJsonLengthDepthAndContainerLimits() {
        val tooLong = " ".repeat(ScriptJsonCodec.MAX_JSON_LENGTH + 1)
        assertFalse(runCatching { ScriptJsonCodec.parse(tooLong) }.isSuccess)

        var deep = "0"
        repeat(ScriptJsonCodec.MAX_DEPTH + 1) { deep = "[$deep]" }
        assertFalse(runCatching { ScriptJsonCodec.parse(deep) }.isSuccess)

        val tooMany = (0..ScriptJsonCodec.MAX_CONTAINER_ITEMS)
            .joinToString(prefix = "[", postfix = "]")
        assertFalse(runCatching { ScriptJsonCodec.parse(tooMany) }.isSuccess)
    }

    @Test
    fun serializesEscapedControlCharactersAndQuotes() {
        val value = linkedMapOf<String, Any?>(
            "quote" to "A \"quote\"",
            "line" to "one\ntwo",
            "slash" to "a\\b"
        )
        val json = ScriptJsonCodec.stringify(value)
        val parsed = ScriptJsonCodec.parse(json) as Map<*, *>
        assertEquals(value, parsed)
    }
}
