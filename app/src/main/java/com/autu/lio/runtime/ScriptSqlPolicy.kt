package com.autu.lio.runtime

import java.util.Locale

/**
 * SQL allow-list and bind-parameter validation for AUTU's private database.
 * This class is platform independent so its safety rules can be unit tested.
 */
object ScriptSqlPolicy {
    const val MAX_SQL_LENGTH = 8_192
    const val MAX_PARAMETERS = 64
    const val MAX_QUERY_ROWS = 256
    const val MAX_QUERY_COLUMNS = 64
    const val MAX_CELL_TEXT_LENGTH = 4_096
    const val MAX_QUERY_RESULT_CHARS = 1_048_576
    const val INTERNAL_MIGRATION_TABLE = "_AUTU_SCHEMA_MIGRATIONS"

    enum class Mode { QUERY, EXECUTE }

    data class ValidatedStatement(
        val sql: String,
        val verb: String,
        val arguments: Array<Any?>,
        val queryArguments: Array<String?>
    )

    private val executeVerbs = setOf("INSERT", "UPDATE", "DELETE", "REPLACE")
    private val forbiddenTokens = setOf(
        "ATTACH", "DETACH", "PRAGMA", "VACUUM", "REINDEX",
        "TRIGGER", "TEMP", "TEMPORARY", "VIRTUAL",
        "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE",
        "LOAD_EXTENSION", "RETURNING"
    )

    fun validate(sql: String, parameters: List<*>, mode: Mode): ValidatedStatement {
        require(sql.isNotBlank()) { "Câu SQL không được để trống." }
        require(sql.length <= MAX_SQL_LENGTH) {
            "Câu SQL vượt quá " + MAX_SQL_LENGTH + " ký tự."
        }
        require(parameters.size <= MAX_PARAMETERS) {
            "SQL chỉ nhận tối đa " + MAX_PARAMETERS + " tham số."
        }
        require(!sql.contains(INTERNAL_MIGRATION_TABLE, ignoreCase = true)) {
            "Bảng lịch sử di trú là dữ liệu nội bộ, không được truy cập trực tiếp."
        }

        val scan = scan(sql)
        val verb = scan.tokens.firstOrNull()
            ?: throw IllegalArgumentException("Không tìm thấy lệnh SQL.")

        when (mode) {
            Mode.QUERY -> require(verb == "SELECT") {
                "SQL_TRUY_VẤN chỉ chấp nhận câu SELECT."
            }
            Mode.EXECUTE -> {
                val second = scan.tokens.getOrNull(1)
                val allowed = when (verb) {
                    "CREATE" -> second == "TABLE" || second == "INDEX" ||
                        (second == "UNIQUE" && scan.tokens.getOrNull(2) == "INDEX")
                    "ALTER" -> second == "TABLE"
                    "DROP" -> second == "TABLE" || second == "INDEX"
                    else -> verb in executeVerbs
                }
                require(allowed) {
                    "SQL_THỰC_THI chỉ cho phép CREATE TABLE/INDEX, ALTER TABLE, DROP TABLE/INDEX, INSERT, UPDATE, DELETE hoặc REPLACE."
                }
            }
        }

        val forbidden = scan.tokens.firstOrNull { it in forbiddenTokens }
        require(forbidden == null) {
            "Câu SQL chứa từ khóa không được phép: " + forbidden + "."
        }
        require(scan.parameterCount == parameters.size) {
            "Câu SQL có " + scan.parameterCount + " dấu ? nhưng nhận " + parameters.size + " tham số."
        }

        val arguments = Array<Any?>(parameters.size) { index ->
            normalizeParameter(parameters[index], index)
        }
        val queryArguments = Array<String?>(arguments.size) { index ->
            when (val value = arguments[index]) {
                null -> null
                else -> value.toString()
            }
        }
        return ValidatedStatement(sql.trim(), verb, arguments, queryArguments)
    }

    private data class ScanResult(val tokens: List<String>, val parameterCount: Int)

    /**
     * Scans SQL outside quoted strings and identifiers. It deliberately rejects
     * comments, semicolons, named parameters and numbered positional parameters.
     */
    private fun scan(sql: String): ScanResult {
        val tokens = mutableListOf<String>()
        var parameterCount = 0
        var index = 0

        while (index < sql.length) {
            val char = sql[index]
            when {
                char.isWhitespace() -> index++

                char == ';' ->
                    throw IllegalArgumentException("Không cho phép dấu ';' hoặc nhiều câu SQL trong một lệnh.")

                char == '-' && index + 1 < sql.length && sql[index + 1] == '-' ->
                    throw IllegalArgumentException("Không cho phép chú thích SQL.")

                char == '/' && index + 1 < sql.length && sql[index + 1] == '*' ->
                    throw IllegalArgumentException("Không cho phép chú thích SQL.")

                char == '?' -> {
                    parameterCount++
                    require(index + 1 >= sql.length || !sql[index + 1].isDigit()) {
                        "Chỉ chấp nhận dấu ? không đánh số cho tham số SQL."
                    }
                    index++
                }

                (char == ':' || char == '@' || char == '$') &&
                    index + 1 < sql.length &&
                    (sql[index + 1].isLetterOrDigit() || sql[index + 1] == '_') ->
                    throw IllegalArgumentException("Chỉ chấp nhận tham số SQL dạng dấu ?.")

                char == '\'' || char == '"' || char == '`' || char == '[' -> {
                    val closing = if (char == '[') ']' else char
                    index++
                    var closed = false
                    while (index < sql.length) {
                        if (sql[index] == closing) {
                            if (index + 1 < sql.length &&
                                sql[index + 1] == closing &&
                                closing != ']'
                            ) {
                                index += 2
                            } else {
                                index++
                                closed = true
                                break
                            }
                        } else {
                            index++
                        }
                    }
                    require(closed) { "Chuỗi hoặc tên cột trong SQL chưa được đóng." }
                }

                char.isLetter() || char == '_' -> {
                    val start = index
                    index++
                    while (index < sql.length &&
                        (sql[index].isLetterOrDigit() || sql[index] == '_')
                    ) {
                        index++
                    }
                    tokens += sql.substring(start, index).uppercase(Locale.ROOT)
                }

                else -> index++
            }
        }

        return ScanResult(tokens, parameterCount)
    }

    private fun normalizeParameter(value: Any?, index: Int): Any? = when (value) {
        null -> null
        is String -> {
            require(value.length <= MAX_CELL_TEXT_LENGTH) {
                "Tham số SQL " + (index + 1) + " vượt quá giới hạn văn bản."
            }
            value
        }
        is Boolean -> if (value) 1L else 0L
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        is Float -> {
            require(value.isFinite()) { "Tham số SQL phải là số hữu hạn." }
            value.toDouble()
        }
        is Double -> {
            require(value.isFinite()) { "Tham số SQL phải là số hữu hạn." }
            value
        }
        else -> throw IllegalArgumentException(
            "Tham số SQL " + (index + 1) + " chỉ nhận RỖNG, chuỗi, số hoặc luận lý; không nhận danh sách/bản đồ."
        )
    }
}
