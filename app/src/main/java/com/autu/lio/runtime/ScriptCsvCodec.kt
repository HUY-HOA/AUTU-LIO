package com.autu.lio.runtime

/**
 * Bounded CSV parser and serializer for AUTU Script.
 *
 * The parser supports quoted delimiters, escaped quotes, CRLF/LF/CR records,
 * quoted line breaks and UTF-8 text already decoded by ScriptFileStore.
 */
object ScriptCsvCodec {
    const val MAX_TEXT_CHARS = 1_048_576
    const val MAX_ROWS = 64
    const val MAX_COLUMNS = 64
    const val MAX_CELLS = 512
    const val MAX_CELL_CHARS = 16_384

    fun parse(source: String, delimiterText: String): List<List<String>> {
        val delimiter = validateDelimiter(delimiterText)
        require(source.length <= MAX_TEXT_CHARS) {
            "Nội dung CSV vượt quá $MAX_TEXT_CHARS ký tự."
        }
        if (source.isEmpty()) return emptyList()

        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var cellCount = 0
        var inQuotes = false
        var afterClosingQuote = false
        var fieldStarted = false
        var recordEnded = false
        var index = if (source.startsWith('\uFEFF')) 1 else 0
        val hasContent = index < source.length

        fun appendCharacter(character: Char) {
            require(field.length < MAX_CELL_CHARS) {
                "Một ô CSV không được vượt quá $MAX_CELL_CHARS ký tự."
            }
            field.append(character)
        }

        fun finishField() {
            require(row.size < MAX_COLUMNS) {
                "Một dòng CSV không được vượt quá $MAX_COLUMNS cột."
            }
            cellCount++
            require(cellCount <= MAX_CELLS) {
                "CSV không được vượt quá $MAX_CELLS ô dữ liệu."
            }
            row += field.toString()
            field.setLength(0)
            fieldStarted = false
            afterClosingQuote = false
        }

        fun finishRecord() {
            finishField()
            require(rows.size < MAX_ROWS) {
                "CSV không được vượt quá $MAX_ROWS dòng."
            }
            rows += row.toList()
            row.clear()
            recordEnded = true
        }

        while (index < source.length) {
            val character = source[index]
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < source.length && source[index + 1] == '"') {
                        appendCharacter('"')
                        index++
                    } else {
                        inQuotes = false
                        afterClosingQuote = true
                    }
                } else {
                    appendCharacter(character)
                }
                recordEnded = false
            } else if (afterClosingQuote) {
                when (character) {
                    delimiter -> {
                        finishField()
                        recordEnded = false
                    }
                    '\r', '\n' -> {
                        finishRecord()
                        if (character == '\r' && index + 1 < source.length && source[index + 1] == '\n') {
                            index++
                        }
                    }
                    else -> throw IllegalArgumentException(
                        "CSV có ký tự không hợp lệ sau dấu nháy đóng ở vị trí ${index + 1}."
                    )
                }
            } else {
                when (character) {
                    delimiter -> {
                        finishField()
                        recordEnded = false
                    }
                    '\r', '\n' -> {
                        finishRecord()
                        if (character == '\r' && index + 1 < source.length && source[index + 1] == '\n') {
                            index++
                        }
                    }
                    '"' -> {
                        require(!fieldStarted && field.isEmpty()) {
                            "CSV chỉ được mở dấu nháy ở đầu ô."
                        }
                        inQuotes = true
                        fieldStarted = true
                        recordEnded = false
                    }
                    else -> {
                        appendCharacter(character)
                        fieldStarted = true
                        recordEnded = false
                    }
                }
            }
            index++
        }

        require(!inQuotes) {
            "CSV kết thúc khi dấu nháy kép vẫn chưa được đóng."
        }
        if (hasContent && (!recordEnded || row.isNotEmpty() || field.isNotEmpty() || fieldStarted || afterClosingQuote)) {
            finishField()
            require(rows.size < MAX_ROWS) {
                "CSV không được vượt quá $MAX_ROWS dòng."
            }
            rows += row.toList()
        }
        return rows
    }

    fun serialize(rows: List<*>, delimiterText: String): String {
        val delimiter = validateDelimiter(delimiterText)
        require(rows.size <= MAX_ROWS) {
            "CSV không được vượt quá $MAX_ROWS dòng."
        }

        val output = StringBuilder()
        var totalCells = 0
        rows.forEachIndexed { rowIndex, rawRow ->
            val cells = rawRow as? List<*>
                ?: throw IllegalArgumentException("Dòng CSV ${rowIndex + 1} phải là DANH_SÁCH.")
            require(cells.size <= MAX_COLUMNS) {
                "Một dòng CSV không được vượt quá $MAX_COLUMNS cột."
            }
            cells.forEachIndexed { cellIndex, value ->
                totalCells++
                require(totalCells <= MAX_CELLS) {
                    "CSV không được vượt quá $MAX_CELLS ô dữ liệu."
                }
                val text = when (value) {
                    null -> ""
                    is String -> value
                    is Boolean -> value.toString()
                    is Number -> {
                        require(value.toDouble().isFinite()) {
                            "CSV không chấp nhận số NaN hoặc vô cực."
                        }
                        value.toString()
                    }
                    else -> throw IllegalArgumentException(
                        "Ô CSV ${rowIndex + 1}, cột ${cellIndex + 1} chỉ nhận chuỗi, số, LUẬN_LÝ hoặc RỖNG."
                    )
                }
                require(text.length <= MAX_CELL_CHARS) {
                    "Một ô CSV không được vượt quá $MAX_CELL_CHARS ký tự."
                }
                if (cellIndex > 0) output.append(delimiter)
                appendEncodedField(output, text, delimiter)
                require(output.length <= MAX_TEXT_CHARS) {
                    "Nội dung CSV đầu ra vượt quá $MAX_TEXT_CHARS ký tự."
                }
            }
            output.append("\r\n")
            require(output.length <= MAX_TEXT_CHARS) {
                "Nội dung CSV đầu ra vượt quá $MAX_TEXT_CHARS ký tự."
            }
        }
        return output.toString()
    }

    private fun validateDelimiter(value: String): Char {
        require(value.length == 1) {
            "Dấu phân cách CSV phải là đúng một ký tự."
        }
        val delimiter = value.single()
        require(delimiter != '"' && delimiter != '\r' && delimiter != '\n' && delimiter != '\u0000') {
            "Dấu phân cách CSV không được là dấu nháy, ký tự xuống dòng hoặc NUL."
        }
        return delimiter
    }

    private fun appendEncodedField(output: StringBuilder, value: String, delimiter: Char) {
        val quote = value.contains(delimiter) ||
            value.contains('"') ||
            value.contains('\r') ||
            value.contains('\n') ||
            value.firstOrNull()?.isWhitespace() == true ||
            value.lastOrNull()?.isWhitespace() == true
        if (!quote) {
            output.append(value)
            return
        }
        output.append('"')
        value.forEach { character ->
            if (character == '"') {
                output.append('"')
                output.append('"')
            } else output.append(character)
        }
        output.append('"')
    }
}
