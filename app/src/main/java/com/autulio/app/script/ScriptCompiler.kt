package com.autulio.app.script

data class ScriptDiagnostic(
    val line: Int,
    val column: Int,
    val code: String,
    val messageVi: String,
    val messageEn: String
)

sealed interface ScriptInstruction {
    data class Tap(val x: Int, val y: Int) : ScriptInstruction
    data class LongPress(val x: Int, val y: Int, val durationMs: Int) : ScriptInstruction
    data class Swipe(
        val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Int
    ) : ScriptInstruction
    data class Wait(val durationMs: Int) : ScriptInstruction
    data class Repeat(val count: Int, val body: List<ScriptInstruction>) : ScriptInstruction
}

data class ScriptProgram(
    val languageVersion: String,
    val instructions: List<ScriptInstruction>,
    val expandedInstructionCount: Long,
    val estimatedDurationMs: Long
)

data class ScriptCompileResult(
    val program: ScriptProgram?,
    val diagnostics: List<ScriptDiagnostic>
) {
    val isSuccess: Boolean
        get() = program != null && diagnostics.isEmpty()
}

object ScriptCompiler {
    const val LANGUAGE_VERSION = "0.1"
    const val MAX_SOURCE_LENGTH = 32_768
    const val MAX_STATEMENTS = 1_000
    const val MAX_REPEAT_COUNT = 100
    const val MAX_NESTING_DEPTH = 8
    const val MAX_EXPANDED_INSTRUCTIONS = 5_000L
    const val MAX_TOTAL_DURATION_MS = 600_000L
    private const val MAX_COORDINATE = 10_000
    private const val MAX_WAIT_MS = 60_000
    private const val MAX_GESTURE_MS = 10_000
    private const val INSTRUCTION_CAP = MAX_EXPANDED_INSTRUCTIONS + 1
    private const val DURATION_CAP = MAX_TOTAL_DURATION_MS + 1

    fun compile(source: String): ScriptCompileResult {
        if (source.length > MAX_SOURCE_LENGTH) {
            return failure(1, 1, "source_too_large",
                "Mã vượt quá giới hạn 32.768 ký tự.",
                "Source exceeds the 32,768-character limit.")
        }

        return try {
            val instructions = Parser(source).parse()
            if (instructions.isEmpty()) {
                return failure(1, 1, "empty_program", "Chưa có lệnh nào.", "The script has no instructions.")
            }

            val metrics = measure(instructions)
            if (metrics.instructions > MAX_EXPANDED_INSTRUCTIONS) {
                return failure(1, 1, "instruction_budget_exceeded",
                    "Kịch bản vượt quá 5.000 lượt thực thi lệnh.",
                    "The script exceeds the 5,000 expanded-instruction limit.")
            }
            if (metrics.durationMs > MAX_TOTAL_DURATION_MS) {
                return failure(1, 1, "duration_budget_exceeded",
                    "Tổng thời lượng chờ và thao tác vượt quá 10 phút.",
                    "Total wait and gesture duration exceeds 10 minutes.")
            }

            ScriptCompileResult(
                ScriptProgram(LANGUAGE_VERSION, instructions, metrics.instructions, metrics.durationMs),
                emptyList()
            )
        } catch (error: CompileFailure) {
            ScriptCompileResult(null, listOf(error.diagnostic))
        }
    }

    private fun failure(
        line: Int, column: Int, code: String, messageVi: String, messageEn: String
    ) = ScriptCompileResult(
        null, listOf(ScriptDiagnostic(line, column, code, messageVi, messageEn))
    )

    private data class Metrics(val instructions: Long, val durationMs: Long)

    private fun measure(instructions: List<ScriptInstruction>): Metrics {
        var count = 0L
        var duration = 0L
        for (instruction in instructions) {
            val item = when (instruction) {
                is ScriptInstruction.Tap -> Metrics(1, 0)
                is ScriptInstruction.LongPress -> Metrics(1, instruction.durationMs.toLong())
                is ScriptInstruction.Swipe -> Metrics(1, instruction.durationMs.toLong())
                is ScriptInstruction.Wait -> Metrics(1, instruction.durationMs.toLong())
                is ScriptInstruction.Repeat -> {
                    val nested = measure(instruction.body)
                    Metrics(
                        cappedMultiply(nested.instructions, instruction.count.toLong(), INSTRUCTION_CAP),
                        cappedMultiply(nested.durationMs, instruction.count.toLong(), DURATION_CAP)
                    )
                }
            }
            count = cappedAdd(count, item.instructions, INSTRUCTION_CAP)
            duration = cappedAdd(duration, item.durationMs, DURATION_CAP)
        }
        return Metrics(count, duration)
    }

    private fun cappedAdd(left: Long, right: Long, cap: Long): Long =
        if (left >= cap || right >= cap || left > cap - right) cap else left + right

    private fun cappedMultiply(value: Long, multiplier: Long, cap: Long): Long =
        if (value == 0L || multiplier == 0L) 0L
        else if (value >= cap || value > cap / multiplier) cap
        else value * multiplier

    private enum class TokenType {
        IDENTIFIER, INTEGER, LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
        COMMA, NEWLINE, SEMICOLON, EOF
    }

    private data class Token(
        val type: TokenType, val text: String, val line: Int, val column: Int
    )

    private class CompileFailure(val diagnostic: ScriptDiagnostic) : RuntimeException()

    private class Lexer(private val source: String) {
        private val tokens = mutableListOf<Token>()
        private var index = 0
        private var line = 1
        private var column = 1

        fun tokenize(): List<Token> {
            while (index < source.length) {
                val current = source[index]
                when {
                    current == ' ' || current == '\t' -> advance()
                    current == '\r' || current == '\n' -> readNewline()
                    current == '/' && peekNext() == '/' -> readComment()
                    current.isLetter() || current == '_' -> readIdentifier()
                    current.isDigit() -> readInteger()
                    else -> readPunctuation(current)
                }
            }
            tokens += Token(TokenType.EOF, "", line, column)
            return tokens
        }

        private fun readIdentifier() {
            val startLine = line
            val startColumn = column
            val start = index
            while (index < source.length &&
                (source[index].isLetterOrDigit() || source[index] == '_')
            ) advance()
            tokens += Token(TokenType.IDENTIFIER, source.substring(start, index), startLine, startColumn)
        }

        private fun readInteger() {
            val startLine = line
            val startColumn = column
            val start = index
            while (index < source.length && source[index].isDigit()) advance()
            tokens += Token(TokenType.INTEGER, source.substring(start, index), startLine, startColumn)
        }

        private fun readNewline() {
            val startLine = line
            val startColumn = column
            if (source[index] == '\r' && peekNext() == '\n') index += 2 else index++
            line++
            column = 1
            tokens += Token(TokenType.NEWLINE, "\n", startLine, startColumn)
        }

        private fun readComment() {
            while (index < source.length && source[index] != '\r' && source[index] != '\n') advance()
        }

        private fun readPunctuation(current: Char) {
            val type = when (current) {
                '(' -> TokenType.LEFT_PAREN
                ')' -> TokenType.RIGHT_PAREN
                '{' -> TokenType.LEFT_BRACE
                '}' -> TokenType.RIGHT_BRACE
                ',' -> TokenType.COMMA
                ';' -> TokenType.SEMICOLON
                else -> fail(line, column, "invalid_character",
                    "Ký tự '" + current + "' không được hỗ trợ.",
                    "Character '" + current + "' is not supported.")
            }
            tokens += Token(type, current.toString(), line, column)
            advance()
        }

        private fun advance() {
            index++
            column++
        }

        private fun peekNext(): Char? =
            if (index + 1 < source.length) source[index + 1] else null
    }

    private class Parser(source: String) {
        private val tokens = Lexer(source).tokenize()
        private var index = 0
        private var statementCount = 0

        fun parse(): List<ScriptInstruction> = parseBlock(nested = false, depth = 0)

        private fun parseBlock(nested: Boolean, depth: Int): List<ScriptInstruction> {
            val instructions = mutableListOf<ScriptInstruction>()
            skipSeparators()

            while (current().type != TokenType.EOF &&
                !(nested && current().type == TokenType.RIGHT_BRACE)
            ) {
                if (!nested && current().type == TokenType.RIGHT_BRACE) {
                    failCurrent("unexpected_brace", "Dấu '}' không có khối lệnh mở.", "Unexpected closing brace.")
                }
                statementCount++
                if (statementCount > MAX_STATEMENTS) {
                    failCurrent("too_many_statements",
                        "Kịch bản vượt quá 1.000 câu lệnh khai báo.",
                        "The script exceeds 1,000 source statements.")
                }
                instructions += parseStatement(depth)
                if (current().type !in setOf(
                        TokenType.NEWLINE, TokenType.SEMICOLON,
                        TokenType.RIGHT_BRACE, TokenType.EOF
                    )
                ) {
                    failCurrent("expected_separator",
                        "Mỗi lệnh phải nằm trên một dòng riêng hoặc kết thúc bằng ';'.",
                        "Separate statements with a newline or semicolon.")
                }
                skipSeparators()
            }

            if (nested && current().type == TokenType.EOF) {
                failCurrent("missing_brace", "Thiếu dấu '}' để đóng repeat.", "Missing '}' to close repeat.")
            }
            return instructions
        }

        private fun parseStatement(depth: Int): ScriptInstruction {
            val name = expect(TokenType.IDENTIFIER, "expected_command",
                "Cần có tên lệnh.", "Expected a command name.")
            if (name.text == "repeat") return parseRepeat(name, depth)
            val arguments = parseArguments()
            return when (name.text) {
                "tap" -> {
                    requireArity(name, arguments, 2)
                    ScriptInstruction.Tap(coordinate(name, arguments[0]), coordinate(name, arguments[1]))
                }
                "longPress" -> {
                    requireArity(name, arguments, 3)
                    ScriptInstruction.LongPress(
                        coordinate(name, arguments[0]), coordinate(name, arguments[1]),
                        duration(name, arguments[2], 100, MAX_GESTURE_MS)
                    )
                }
                "swipe" -> {
                    requireArity(name, arguments, 5)
                    ScriptInstruction.Swipe(
                        coordinate(name, arguments[0]), coordinate(name, arguments[1]),
                        coordinate(name, arguments[2]), coordinate(name, arguments[3]),
                        duration(name, arguments[4], 1, MAX_GESTURE_MS)
                    )
                }
                "wait" -> {
                    requireArity(name, arguments, 1)
                    ScriptInstruction.Wait(duration(name, arguments[0], 1, MAX_WAIT_MS))
                }
                else -> fail(name.line, name.column, "unknown_command",
                    "Không nhận ra lệnh '" + name.text + "'.",
                    "Unknown command '" + name.text + "'.")
            }
        }

        private fun parseRepeat(name: Token, depth: Int): ScriptInstruction {
            if (depth >= MAX_NESTING_DEPTH) {
                fail(name.line, name.column, "nesting_limit",
                    "Repeat lồng nhau tối đa 8 cấp.", "Repeat nesting is limited to 8 levels.")
            }
            expect(TokenType.LEFT_PAREN, "expected_left_parenthesis", "Thiếu dấu '('.", "Expected '('.")
            val countToken = expect(TokenType.INTEGER, "expected_integer",
                "Repeat cần số lần lặp nguyên.", "Repeat requires an integer count.")
            val count = countToken.text.toIntOrNull()
                ?: fail(countToken.line, countToken.column, "invalid_integer", "Số nguyên không hợp lệ.", "Invalid integer.")
            if (count !in 1..MAX_REPEAT_COUNT) {
                fail(countToken.line, countToken.column, "repeat_range",
                    "Số lần lặp phải từ 1 đến 100.", "Repeat count must be from 1 to 100.")
            }
            expect(TokenType.RIGHT_PAREN, "expected_right_parenthesis", "Thiếu dấu ')'.", "Expected ')'.")
            expect(TokenType.LEFT_BRACE, "expected_left_brace",
                "Thiếu dấu '{' mở khối repeat.", "Expected '{' to open repeat.")
            val body = parseBlock(nested = true, depth = depth + 1)
            expect(TokenType.RIGHT_BRACE, "expected_right_brace", "Thiếu dấu '}'.", "Expected '}'.")
            if (body.isEmpty()) {
                fail(name.line, name.column, "empty_repeat",
                    "Khối repeat không được để trống.", "A repeat block cannot be empty.")
            }
            return ScriptInstruction.Repeat(count, body)
        }

        private fun parseArguments(): List<Int> {
            expect(TokenType.LEFT_PAREN, "expected_left_parenthesis", "Thiếu dấu '('.", "Expected '('.")
            val arguments = mutableListOf<Int>()
            if (current().type != TokenType.RIGHT_PAREN) {
                while (true) {
                    val token = expect(TokenType.INTEGER, "expected_integer",
                        "Tham số phải là số nguyên không âm.", "Arguments must be non-negative integers.")
                    arguments += token.text.toIntOrNull()
                        ?: fail(token.line, token.column, "invalid_integer", "Số nguyên không hợp lệ.", "Invalid integer.")
                    if (current().type != TokenType.COMMA) break
                    advance()
                }
            }
            expect(TokenType.RIGHT_PAREN, "expected_right_parenthesis", "Thiếu dấu ')'.", "Expected ')'.")
            return arguments
        }

        private fun requireArity(name: Token, arguments: List<Int>, expected: Int) {
            if (arguments.size != expected) {
                fail(name.line, name.column, "argument_count",
                    "Lệnh '" + name.text + "' cần đúng " + expected + " tham số.",
                    "'" + name.text + "' requires exactly " + expected + " arguments.")
            }
        }

        private fun coordinate(name: Token, value: Int): Int {
            if (value !in 0..MAX_COORDINATE) {
                fail(name.line, name.column, "coordinate_range",
                    "Tọa độ phải nằm trong khoảng 0 đến " + MAX_COORDINATE + ".",
                    "Coordinates must be between 0 and " + MAX_COORDINATE + ".")
            }
            return value
        }

        private fun duration(name: Token, value: Int, min: Int, max: Int): Int {
            if (value !in min..max) {
                fail(name.line, name.column, "duration_range",
                    "Thời lượng của '" + name.text + "' phải từ " + min + " đến " + max + " ms.",
                    "The duration for '" + name.text + "' must be between " + min + " and " + max + " ms.")
            }
            return value
        }

        private fun skipSeparators() {
            while (current().type == TokenType.NEWLINE || current().type == TokenType.SEMICOLON) advance()
        }

        private fun expect(
            type: TokenType, code: String, messageVi: String, messageEn: String
        ): Token {
            if (current().type != type) failCurrent(code, messageVi, messageEn)
            return advance()
        }

        private fun current(): Token = tokens[index]

        private fun advance(): Token {
            val token = current()
            if (token.type != TokenType.EOF) index++
            return token
        }

        private fun failCurrent(code: String, messageVi: String, messageEn: String): Nothing {
            val token = current()
            fail(token.line, token.column, code, messageVi, messageEn)
        }
    }

    private fun fail(
        line: Int, column: Int, code: String, messageVi: String, messageEn: String
    ): Nothing {
        throw CompileFailure(ScriptDiagnostic(line, column, code, messageVi, messageEn))
    }
}
