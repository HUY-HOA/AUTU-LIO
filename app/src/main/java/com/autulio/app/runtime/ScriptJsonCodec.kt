package com.autulio.app.runtime

/**
 * Strict, dependency-free JSON codec for AUTU Script.
 *
 * Values are represented as Double, String, Boolean, null, MutableList and
 * insertion-ordered MutableMap<String, Any?>. Limits prevent scripts from
 * constructing extremely large or deeply nested values.
 */
internal object ScriptJsonCodec {
    const val MAX_JSON_LENGTH = 65_536
    const val MAX_DEPTH = 8
    const val MAX_CONTAINER_ITEMS = 64
    const val MAX_TOTAL_VALUES = 512

    fun parse(source: String): Any? {
        require(source.length <= MAX_JSON_LENGTH) {
            "JSON vượt quá giới hạn $MAX_JSON_LENGTH ký tự."
        }
        val parser = Parser(source)
        val value = parser.readValue(0)
        parser.skipWhitespace()
        require(parser.isAtEnd()) { "JSON có dữ liệu thừa sau giá trị cuối cùng." }
        return value
    }

    fun stringify(value: Any?): String {
        val output = StringBuilder()
        val budget = intArrayOf(0)
        writeValue(value, output, 0, budget)
        require(output.length <= MAX_JSON_LENGTH) {
            "JSON sau khi chuyển đổi vượt quá giới hạn $MAX_JSON_LENGTH ký tự."
        }
        return output.toString()
    }

    private fun writeValue(
        value: Any?,
        output: StringBuilder,
        depth: Int,
        budget: IntArray
    ) {
        budget[0]++
        require(budget[0] <= MAX_TOTAL_VALUES) {
            "JSON vượt quá giới hạn $MAX_TOTAL_VALUES giá trị."
        }
        when (value) {
            null -> output.append("null")
            is String -> writeString(value, output)
            is Boolean -> output.append(if (value) "true" else "false")
            is Number -> {
                val number = value.toDouble()
                require(number.isFinite()) { "JSON không hỗ trợ số NaN hoặc vô hạn." }
                output.append(number.toString())
            }
            is List<*> -> {
                require(depth < MAX_DEPTH) { "JSON lồng nhau quá $MAX_DEPTH cấp." }
                require(value.size <= MAX_CONTAINER_ITEMS) {
                    "Mảng JSON vượt quá $MAX_CONTAINER_ITEMS phần tử."
                }
                output.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) output.append(',')
                    writeValue(item, output, depth + 1, budget)
                }
                output.append(']')
            }
            is Map<*, *> -> {
                require(depth < MAX_DEPTH) { "JSON lồng nhau quá $MAX_DEPTH cấp." }
                require(value.size <= MAX_CONTAINER_ITEMS) {
                    "Đối tượng JSON vượt quá $MAX_CONTAINER_ITEMS khóa."
                }
                output.append('{')
                value.entries.forEachIndexed { index, entry ->
                    val key = entry.key as? String
                        ?: throw IllegalArgumentException("Khóa đối tượng JSON phải là chuỗi.")
                    if (index > 0) output.append(',')
                    writeString(key, output)
                    output.append(':')
                    writeValue(entry.value, output, depth + 1, budget)
                }
                output.append('}')
            }
            else -> throw IllegalArgumentException("Kiểu dữ liệu không thể chuyển thành JSON.")
        }
        require(output.length <= MAX_JSON_LENGTH) {
            "JSON sau khi chuyển đổi vượt quá giới hạn $MAX_JSON_LENGTH ký tự."
        }
    }

    private fun writeString(value: String, output: StringBuilder) {
        output.append('"')
        for (char in value) {
            when (char) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        output.append("\\u")
                        output.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        output.append(char)
                    }
                }
            }
        }
        output.append('"')
    }

    private class Parser(private val source: String) {
        private var index = 0
        private var valueCount = 0

        fun isAtEnd(): Boolean = index >= source.length

        fun skipWhitespace() {
            while (index < source.length && source[index] in charArrayOf(' ', '\t', '\r', '\n')) {
                index++
            }
        }

        fun readValue(depth: Int): Any? {
            skipWhitespace()
            require(depth <= MAX_DEPTH) { "JSON lồng nhau quá $MAX_DEPTH cấp." }
            valueCount++
            require(valueCount <= MAX_TOTAL_VALUES) {
                "JSON vượt quá giới hạn $MAX_TOTAL_VALUES giá trị."
            }
            require(index < source.length) { "JSON kết thúc đột ngột." }
            return when (source[index]) {
                '"' -> readString()
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                't' -> {
                    readKeyword("true")
                    true
                }
                'f' -> {
                    readKeyword("false")
                    false
                }
                'n' -> {
                    readKeyword("null")
                    null
                }
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("Giá trị JSON không hợp lệ tại vị trí $index.")
            }
        }

        private fun readObject(depth: Int): MutableMap<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                require(index < source.length && source[index] == '"') {
                    "Tên khóa JSON phải là chuỗi tại vị trí $index."
                }
                val key = readString()
                require(key.isNotEmpty()) { "Khóa JSON không được để trống." }
                require(!result.containsKey(key)) { "Khóa JSON bị lặp: $key." }
                require(result.size < MAX_CONTAINER_ITEMS) {
                    "Đối tượng JSON vượt quá $MAX_CONTAINER_ITEMS khóa."
                }
                skipWhitespace()
                expect(':')
                result[key] = readValue(depth + 1)
                skipWhitespace()
                if (consume('}')) break
                expect(',')
            }
            return result
        }

        private fun readArray(depth: Int): MutableList<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (consume(']')) return result
            while (true) {
                require(result.size < MAX_CONTAINER_ITEMS) {
                    "Mảng JSON vượt quá $MAX_CONTAINER_ITEMS phần tử."
                }
                result += readValue(depth + 1)
                skipWhitespace()
                if (consume(']')) break
                expect(',')
            }
            return result
        }

        private fun readString(): String {
            expect('"')
            val output = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                when {
                    char == '"' -> {
                        require(output.length <= 4_096) { "Một chuỗi JSON vượt quá 4.096 ký tự." }
                        return output.toString()
                    }
                    char == '\\' -> {
                        require(index < source.length) { "Escape JSON chưa hoàn chỉnh." }
                        when (val escaped = source[index++]) {
                            '"' -> output.append('"')
                            '\\' -> output.append('\\')
                            '/' -> output.append('/')
                            'b' -> output.append('\b')
                            'f' -> output.append('\u000C')
                            'n' -> output.append('\n')
                            'r' -> output.append('\r')
                            't' -> output.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) { "Escape Unicode JSON chưa hoàn chỉnh." }
                                val code = source.substring(index, index + 4).toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Escape Unicode JSON không hợp lệ.")
                                output.append(code.toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("Escape JSON không hợp lệ: \\$escaped.")
                        }
                    }
                    char.code < 0x20 -> throw IllegalArgumentException("Chuỗi JSON chứa ký tự điều khiển chưa escape.")
                    else -> output.append(char)
                }
                require(output.length <= 4_096) { "Một chuỗi JSON vượt quá 4.096 ký tự." }
            }
            throw IllegalArgumentException("Chuỗi JSON chưa đóng dấu nháy.")
        }

        private fun readNumber(): Double {
            val start = index
            consume('-')
            require(index < source.length) { "Số JSON không hợp lệ." }
            if (consume('0')) {
                require(index >= source.length || !source[index].isDigit()) {
                    "Số JSON không được có số 0 thừa ở đầu."
                }
            } else {
                readDigits(required = true)
            }
            if (consume('.')) readDigits(required = true)
            if (consume('e') || consume('E')) {
                if (!consume('+')) consume('-')
                readDigits(required = true)
            }
            val value = source.substring(start, index).toDoubleOrNull()
                ?: throw IllegalArgumentException("Số JSON không hợp lệ.")
            require(value.isFinite()) { "JSON không hỗ trợ số NaN hoặc vô hạn." }
            return value
        }

        private fun readDigits(required: Boolean) {
            val start = index
            while (index < source.length && source[index].isDigit()) index++
            if (required) require(index > start) { "Số JSON thiếu chữ số." }
        }

        private fun readKeyword(keyword: String) {
            require(source.regionMatches(index, keyword, 0, keyword.length)) {
                "Từ khóa JSON không hợp lệ tại vị trí $index."
            }
            index += keyword.length
        }

        private fun expect(char: Char) {
            require(consume(char)) { "JSON thiếu ký tự '$char' tại vị trí $index." }
        }

        private fun consume(char: Char): Boolean {
            if (index < source.length && source[index] == char) {
                index++
                return true
            }
            return false
        }
    }
}
