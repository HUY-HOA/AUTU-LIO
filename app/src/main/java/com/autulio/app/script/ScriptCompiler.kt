package com.autulio.app.script

import java.util.Locale

data class ScriptDiagnostic(
    val line: Int,
    val column: Int,
    val code: String,
    val messageVi: String,
    val messageEn: String
)

sealed interface ScriptExpression {
    data class NumberLiteral(val value: Double) : ScriptExpression
    data class StringLiteral(val value: String) : ScriptExpression
    data class BooleanLiteral(val value: Boolean) : ScriptExpression
    data class Variable(val name: String) : ScriptExpression
    data class Unary(val operator: String, val operand: ScriptExpression) : ScriptExpression
    data class Binary(val operator: String, val left: ScriptExpression, val right: ScriptExpression) : ScriptExpression
    data class FunctionCall(val name: String, val arguments: List<ScriptExpression>) : ScriptExpression
}

sealed interface ScriptInstruction {
    data class Command(val name: String, val arguments: List<ScriptExpression>) : ScriptInstruction
    data class SetVariable(val name: String, val value: ScriptExpression) : ScriptInstruction
    data class If(val condition: ScriptExpression, val thenBody: List<ScriptInstruction>, val elseBody: List<ScriptInstruction>) : ScriptInstruction
    data class Repeat(val count: ScriptExpression, val body: List<ScriptInstruction>) : ScriptInstruction
    data class While(val condition: ScriptExpression, val body: List<ScriptInstruction>) : ScriptInstruction
    data object BreakLoop : ScriptInstruction
    data object ContinueLoop : ScriptInstruction
}

data class ScriptMetrics(val expandedInstructionCount: Long, val estimatedDurationMs: Long)

data class ScriptProgram(
    val languageVersion: String,
    val instructions: List<ScriptInstruction>,
    val expandedInstructionCount: Long,
    val estimatedDurationMs: Long
)

data class ScriptCompileResult(val program: ScriptProgram?, val diagnostics: List<ScriptDiagnostic>) {
    val isSuccess: Boolean get() = program != null && diagnostics.isEmpty()
}

object ScriptCompiler {
    const val LANGUAGE_VERSION = "0.2"
    const val MAX_SOURCE_LENGTH = 32_768
    const val MAX_STATEMENTS = 1_000
    const val MAX_REPEAT_COUNT = 100
    const val MAX_WHILE_ITERATIONS = 900
    const val MAX_NESTING_DEPTH = 8
    const val MAX_EXPANDED_INSTRUCTIONS = 5_000L
    const val MAX_TOTAL_DURATION_MS = 600_000L
    const val MAX_COORDINATE = 10_000
    const val MAX_WAIT_MS = 60_000
    const val MAX_GESTURE_MS = 10_000
    private const val INSTRUCTION_CAP = MAX_EXPANDED_INSTRUCTIONS + 1
    private const val DURATION_CAP = MAX_TOTAL_DURATION_MS + 1

    private val commandAliases = mapOf(
        "CHẠM" to "CHẠM", "TAP" to "CHẠM",
        "NHẤN_GIỮ" to "NHẤN_GIỮ", "LONGPRESS" to "NHẤN_GIỮ",
        "VUỐT" to "VUỐT", "SWIPE" to "VUỐT",
        "CHỜ" to "CHỜ", "WAIT" to "CHỜ",
        "MỞ_ỨNG_DỤNG" to "MỞ_ỨNG_DỤNG", "OPENAPP" to "MỞ_ỨNG_DỤNG"
    )

    fun compile(source: String): ScriptCompileResult {
        if (source.length > MAX_SOURCE_LENGTH) {
            return failure(1, 1, "source_too_large", "Mã vượt quá giới hạn 32.768 ký tự.", "Source exceeds the 32,768-character limit.")
        }
        return try {
            val instructions = Parser(source).parse()
            if (instructions.isEmpty()) {
                return failure(1, 1, "empty_program", "Chưa có lệnh nào.", "The script has no instructions.")
            }
            val metrics = measureProgram(instructions)
            if (metrics.expandedInstructionCount > MAX_EXPANDED_INSTRUCTIONS) {
                return failure(1, 1, "instruction_budget_exceeded", "Kịch bản có thể vượt quá 5.000 lượt thực thi lệnh.", "The script may exceed the 5,000 expanded-instruction limit.")
            }
            if (metrics.estimatedDurationMs > MAX_TOTAL_DURATION_MS) {
                return failure(1, 1, "duration_budget_exceeded", "Thời lượng ước tính vượt quá 10 phút.", "The estimated execution duration exceeds 10 minutes.")
            }
            ScriptCompileResult(
                ScriptProgram(LANGUAGE_VERSION, instructions, metrics.expandedInstructionCount, metrics.estimatedDurationMs),
                emptyList()
            )
        } catch (error: CompileFailure) {
            ScriptCompileResult(null, listOf(error.diagnostic))
        } catch (_: StackOverflowError) {
            failure(1, 1, "nesting_limit", "Cấu trúc mã lồng nhau quá sâu.", "The script nesting depth is too large.")
        }
    }

    fun measureProgram(instructions: List<ScriptInstruction>): ScriptMetrics {
        val metrics = measureBlock(instructions, 0)
        return ScriptMetrics(metrics.first, metrics.second)
    }

    private fun measureBlock(instructions: List<ScriptInstruction>, depth: Int): Pair<Long, Long> {
        if (depth > MAX_NESTING_DEPTH) return INSTRUCTION_CAP to DURATION_CAP
        var count = 0L
        var duration = 0L
        for (instruction in instructions) {
            val item = when (instruction) {
                is ScriptInstruction.Command -> {
                    val actionDuration = when (instruction.name) {
                        "CHỜ" -> expressionUpperBound(instruction.arguments.firstOrNull(), MAX_WAIT_MS.toLong())
                        "NHẤN_GIỮ", "VUỐT" -> expressionUpperBound(instruction.arguments.lastOrNull(), MAX_GESTURE_MS.toLong())
                        else -> 0L
                    }
                    1L to actionDuration
                }
                is ScriptInstruction.SetVariable -> 1L to 0L
                is ScriptInstruction.If -> {
                    val yes = measureBlock(instruction.thenBody, depth + 1)
                    val no = measureBlock(instruction.elseBody, depth + 1)
                    cappedAdd(1L, maxOf(yes.first, no.first), INSTRUCTION_CAP) to maxOf(yes.second, no.second)
                }
                is ScriptInstruction.Repeat -> {
                    val nested = measureBlock(instruction.body, depth + 1)
                    val iterations = expressionUpperBound(instruction.count, MAX_REPEAT_COUNT.toLong()).coerceIn(0L, MAX_REPEAT_COUNT.toLong())
                    cappedMultiply(nested.first, iterations, INSTRUCTION_CAP) to cappedMultiply(nested.second, iterations, DURATION_CAP)
                }
                is ScriptInstruction.While -> {
                    val nested = measureBlock(instruction.body, depth + 1)
                    cappedMultiply(cappedAdd(nested.first, 1L, INSTRUCTION_CAP), MAX_WHILE_ITERATIONS.toLong(), INSTRUCTION_CAP) to
                        cappedMultiply(nested.second, MAX_WHILE_ITERATIONS.toLong(), DURATION_CAP)
                }
                ScriptInstruction.BreakLoop, ScriptInstruction.ContinueLoop -> 1L to 0L
            }
            count = cappedAdd(count, item.first, INSTRUCTION_CAP)
            duration = cappedAdd(duration, item.second, DURATION_CAP)
        }
        return count to duration
    }

    private fun constantNumber(expression: ScriptExpression): Double? {
        return when (expression) {
            is ScriptExpression.NumberLiteral -> expression.value
            is ScriptExpression.Unary -> {
                val value = constantNumber(expression.operand) ?: return null
                when (expression.operator) {
                    "-" -> -value
                    "+" -> value
                    else -> null
                }
            }
            is ScriptExpression.Binary -> {
                val left = constantNumber(expression.left) ?: return null
                val right = constantNumber(expression.right) ?: return null
                when (expression.operator) {
                    "+" -> left + right
                    "-" -> left - right
                    "*" -> left * right
                    "/" -> if (right == 0.0) null else left / right
                    "%" -> if (right == 0.0) null else left % right
                    else -> null
                }?.takeIf { it.isFinite() }
            }
            else -> null
        }
    }

    private fun expressionUpperBound(expression: ScriptExpression?, fallback: Long): Long =
        (expression as? ScriptExpression.NumberLiteral)?.value?.takeIf { it.isFinite() && it >= 0.0 }?.toLong() ?: fallback

    private fun cappedAdd(left: Long, right: Long, cap: Long): Long =
        if (left >= cap || right >= cap || left > cap - right) cap else left + right

    private fun cappedMultiply(value: Long, multiplier: Long, cap: Long): Long =
        if (value == 0L || multiplier == 0L) 0L else if (value >= cap || value > cap / multiplier) cap else value * multiplier

    private enum class TokenType { IDENTIFIER, NUMBER, STRING, SYMBOL, NEWLINE, SEMICOLON, EOF }
    private data class Token(val type: TokenType, val text: String, val line: Int, val column: Int)
    private class CompileFailure(val diagnostic: ScriptDiagnostic) : RuntimeException()

    private class Lexer(private val source: String) {
        private val tokens = mutableListOf<Token>()
        private var index = 0
        private var line = 1
        private var column = 1

        fun tokenize(): List<Token> {
            while (index < source.length) {
                when {
                    source[index] == ' ' || source[index] == '\t' -> advance()
                    source[index] == '\r' || source[index] == '\n' -> readNewline()
                    source[index] == '/' && peekNext() == '/' -> readComment()
                    source[index] == '"' -> readString()
                    source[index].isDigit() -> readNumber()
                    source[index].isLetter() || source[index] == '_' -> readIdentifier()
                    else -> readSymbol()
                }
            }
            tokens += Token(TokenType.EOF, "", line, column)
            return tokens
        }

        private fun readIdentifier() {
            val startLine = line
            val startColumn = column
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) advance()
            tokens += Token(TokenType.IDENTIFIER, source.substring(start, index), startLine, startColumn)
        }

        private fun readNumber() {
            val startLine = line
            val startColumn = column
            val start = index
            while (index < source.length && source[index].isDigit()) advance()
            if (index < source.length && source[index] == '.' && index + 1 < source.length && source[index + 1].isDigit()) {
                advance()
                while (index < source.length && source[index].isDigit()) advance()
            }
            tokens += Token(TokenType.NUMBER, source.substring(start, index), startLine, startColumn)
        }

        private fun readString() {
            val startLine = line
            val startColumn = column
            advance()
            val value = StringBuilder()
            while (index < source.length && source[index] != '"') {
                val char = source[index]
                if (char == '\\') {
                    advance()
                    if (index >= source.length) fail(startLine, startColumn, "unterminated_string", "Chuỗi văn bản chưa đóng.", "Unterminated string.")
                    value.append(when (val escaped = source[index]) {
                        'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; '\\' -> '\\'; '"' -> '"'; else -> escaped
                    })
                    advance()
                } else {
                    if (char == '\r' || char == '\n') fail(startLine, startColumn, "unterminated_string", "Chuỗi văn bản không được xuống dòng.", "Strings cannot contain raw newlines.")
                    value.append(char)
                    advance()
                }
            }
            if (index >= source.length) fail(startLine, startColumn, "unterminated_string", "Thiếu dấu nháy kép đóng chuỗi.", "Missing closing quote.")
            advance()
            tokens += Token(TokenType.STRING, value.toString(), startLine, startColumn)
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

        private fun readSymbol() {
            val startLine = line
            val startColumn = column
            val current = source[index]
            val next = peekNext()
            val pair = if (next != null && (
                (current == '=' && next == '=') || (current == '!' && next == '=') ||
                    (current == '>' && next == '=') || (current == '<' && next == '=')
            )) "$current$next" else null
            if (pair != null) {
                tokens += Token(TokenType.SYMBOL, pair, startLine, startColumn)
                advance()
                advance()
                return
            }
            if (current in "(){} ,;+-*/%=!<>") {
                if (current == ';') tokens += Token(TokenType.SEMICOLON, ";", startLine, startColumn)
                else if (current != ' ') tokens += Token(TokenType.SYMBOL, current.toString(), startLine, startColumn)
                advance()
                return
            }
            fail(startLine, startColumn, "invalid_character", "Ký tự '$current' không được hỗ trợ.", "Character '$current' is not supported.")
        }

        private fun advance() { index++; column++ }
        private fun peekNext(): Char? = if (index + 1 < source.length) source[index + 1] else null
    }

    private class Parser(source: String) {
        private val tokens = Lexer(source).tokenize()
        private var index = 0
        private var statementCount = 0

        fun parse(): List<ScriptInstruction> = parseBlock(false, 0, 0)

        private fun parseBlock(expectClosingBrace: Boolean, depth: Int, loopDepth: Int): List<ScriptInstruction> {
            val instructions = mutableListOf<ScriptInstruction>()
            skipSeparators()
            while (current().type != TokenType.EOF && !(expectClosingBrace && isSymbol("}"))) {
                if (isSymbol("}")) failCurrent("unexpected_brace", "Dấu '}' không có khối lệnh mở.", "Unexpected closing brace.")
                statementCount++
                if (statementCount > MAX_STATEMENTS) failCurrent("too_many_statements", "Kịch bản vượt quá 1.000 câu lệnh.", "The script exceeds 1,000 statements.")
                instructions += parseStatement(depth, loopDepth)
                if (current().type !in setOf(TokenType.NEWLINE, TokenType.SEMICOLON, TokenType.EOF) && !isSymbol("}")) {
                    failCurrent("expected_separator", "Mỗi lệnh phải nằm trên một dòng riêng hoặc kết thúc bằng ';'.", "Separate statements with a newline or semicolon.")
                }
                skipSeparators()
            }
            if (expectClosingBrace && current().type == TokenType.EOF) failCurrent("missing_brace", "Thiếu dấu '}' để đóng khối lệnh.", "Missing '}' to close the block.")
            return instructions
        }

        private fun parseStatement(depth: Int, loopDepth: Int): ScriptInstruction {
            val name = expect(TokenType.IDENTIFIER, "expected_command", "Cần có lệnh hoặc biến.", "Expected a command or variable.")
            val rawKeyword = normalize(name.text)
            val keyword = if (rawKeyword == "REPEAT") "LẶP" else rawKeyword
            return when (keyword) {
                "ĐẶT" -> parseVariableDeclaration(name)
                "NẾU" -> parseIf(name, depth, loopDepth)
                "LẶP" -> parseRepeat(name, depth, loopDepth)
                "LẶP_KHI" -> parseWhile(name, depth, loopDepth)
                "THOÁT_LẶP" -> {
                    requireInsideLoop(name, loopDepth)
                    optionalEmptyCall()
                    ScriptInstruction.BreakLoop
                }
                "BỎ_QUA_LƯỢT" -> {
                    requireInsideLoop(name, loopDepth)
                    optionalEmptyCall()
                    ScriptInstruction.ContinueLoop
                }
                else -> {
                    if (isSymbol("=")) {
                        advance()
                        ScriptInstruction.SetVariable(name.text, parseExpression())
                    } else parseCommand(name)
                }
            }
        }

        private fun parseVariableDeclaration(name: Token): ScriptInstruction.SetVariable {
            val variable = expect(TokenType.IDENTIFIER, "expected_variable_name", "Sau ĐẶT cần có tên biến.", "Expected a variable name after ĐẶT.")
            expectSymbol("=", "expected_assignment", "Thiếu dấu '=' khi gán biến.", "Expected '=' in variable assignment.")
            return ScriptInstruction.SetVariable(variable.text, parseExpression())
        }

        private fun parseIf(name: Token, depth: Int, loopDepth: Int): ScriptInstruction.If {
            requireNesting(name, depth)
            val condition = parseParenthesizedExpression()
            val thenBody = parseBraceBlock(depth + 1, loopDepth)
            if (thenBody.isEmpty()) fail(name.line, name.column, "empty_if", "Khối NẾU không được để trống.", "The NẾU block cannot be empty.")
            val afterThenBlock = index
            skipSeparators()
            val elseBody = when {
                isIdentifier("NGƯỢC_LẠI") -> {
                    advance()
                    parseBraceBlock(depth + 1, loopDepth)
                }
                isIdentifier("NGƯỢC_LẠI_NẾU") -> {
                    val elseToken = advance()
                    listOf(parseIf(elseToken, depth, loopDepth))
                }
                else -> {
                    index = afterThenBlock
                    emptyList()
                }
            }
            return ScriptInstruction.If(condition, thenBody, elseBody)
        }

        private fun parseRepeat(name: Token, depth: Int, loopDepth: Int): ScriptInstruction.Repeat {
            requireNesting(name, depth)
            val count = parseParenthesizedExpression()
            val literal = (count as? ScriptExpression.NumberLiteral)?.value
            if (literal != null && (!literal.isFinite() || literal < 1.0 || literal > MAX_REPEAT_COUNT)) {
                fail(name.line, name.column, "repeat_range", "Số lần lặp phải từ 1 đến $MAX_REPEAT_COUNT.", "Repeat count must be from 1 to $MAX_REPEAT_COUNT.")
            }
            val body = parseBraceBlock(depth + 1, loopDepth + 1)
            if (body.isEmpty()) fail(name.line, name.column, "empty_repeat", "Khối lặp không được để trống.", "A loop body cannot be empty.")
            return ScriptInstruction.Repeat(count, body)
        }

        private fun parseWhile(name: Token, depth: Int, loopDepth: Int): ScriptInstruction.While {
            requireNesting(name, depth)
            val condition = parseParenthesizedExpression()
            val body = parseBraceBlock(depth + 1, loopDepth + 1)
            if (body.isEmpty()) fail(name.line, name.column, "empty_while", "Khối LẶP_KHI không được để trống.", "A LẶP_KHI block cannot be empty.")
            return ScriptInstruction.While(condition, body)
        }

        private fun parseBraceBlock(depth: Int, loopDepth: Int): List<ScriptInstruction> {
            expectSymbol("{", "expected_left_brace", "Thiếu dấu '{' mở khối lệnh.", "Expected '{' to open a block.")
            val body = parseBlock(true, depth, loopDepth)
            expectSymbol("}", "expected_right_brace", "Thiếu dấu '}' đóng khối lệnh.", "Expected '}' to close the block.")
            return body
        }

        private fun parseCommand(name: Token): ScriptInstruction.Command {
            val normalized = normalize(name.text)
            val command = commandAliases[normalized]
                ?: fail(name.line, name.column, "unknown_command", "Không nhận ra lệnh '" + name.text + "'.", "Unknown command '" + name.text + "'.")
            val args = parseArguments()
            val expected = when (command) {
                "CHẠM" -> 2
                "NHẤN_GIỮ" -> 3
                "VUỐT" -> 5
                "CHỜ", "MỞ_ỨNG_DỤNG" -> 1
                else -> 0
            }
            if (args.size != expected) fail(name.line, name.column, "argument_count", "Lệnh '$command' cần đúng $expected tham số.", "'$command' requires exactly $expected arguments.")
            validateLiteralArguments(name, command, args)
            return ScriptInstruction.Command(command, args)
        }

        private fun validateLiteralArguments(name: Token, command: String, args: List<ScriptExpression>) {
            val coordinateIndexes = when (command) {
                "CHẠM", "NHẤN_GIỮ" -> setOf(0, 1)
                "VUỐT" -> setOf(0, 1, 2, 3)
                else -> emptySet()
            }
            for (index in coordinateIndexes) {
                val value = constantNumber(args[index]) ?: continue
                if (!value.isFinite() || value < 0.0 || value > MAX_COORDINATE) {
                    fail(name.line, name.column, "coordinate_range", "Tọa độ phải từ 0 đến $MAX_COORDINATE.", "Coordinates must be between 0 and $MAX_COORDINATE.")
                }
            }
            val durationIndex = if (command in setOf("CHỜ", "NHẤN_GIỮ", "VUỐT")) args.lastIndex else -1
            if (durationIndex >= 0) {
                val value = constantNumber(args[durationIndex]) ?: return
                val min = if (command == "NHẤN_GIỮ") 100.0 else 1.0
                val max = if (command == "CHỜ") MAX_WAIT_MS.toDouble() else MAX_GESTURE_MS.toDouble()
                if (!value.isFinite() || value < min || value > max) {
                    fail(name.line, name.column, "duration_range", "Thời lượng lệnh '$command' không hợp lệ.", "The duration for '$command' is outside the supported range.")
                }
            }
        }

        private fun parseArguments(): List<ScriptExpression> {
            expectSymbol("(", "expected_left_parenthesis", "Thiếu dấu '(' mở tham số.", "Expected '('.")
            val values = mutableListOf<ScriptExpression>()
            if (!isSymbol(")")) {
                while (true) {
                    values += parseExpression()
                    if (!isSymbol(",")) break
                    advance()
                }
            }
            expectSymbol(")", "expected_right_parenthesis", "Thiếu dấu ')' đóng tham số.", "Expected ')'.")
            return values
        }

        private fun parseParenthesizedExpression(): ScriptExpression {
            expectSymbol("(", "expected_left_parenthesis", "Thiếu dấu '(' mở biểu thức.", "Expected '('.")
            val expression = parseExpression()
            expectSymbol(")", "expected_right_parenthesis", "Thiếu dấu ')' đóng biểu thức.", "Expected ')'.")
            return expression
        }

        private fun parseExpression(minPrecedence: Int = 0): ScriptExpression {
            var left = parsePrefix()
            while (true) {
                val operator = currentOperator() ?: break
                val priority = precedence(operator)
                if (priority < minPrecedence) break
                advance()
                val right = parseExpression(priority + 1)
                left = ScriptExpression.Binary(operator, left, right)
            }
            return left
        }

        private fun parsePrefix(): ScriptExpression {
            val token = current()
            if (token.type == TokenType.SYMBOL && token.text in setOf("!", "-", "+")) {
                advance()
                return ScriptExpression.Unary(if (token.text == "!") "KHÔNG" else token.text, parseExpression(7))
            }
            if (token.type == TokenType.IDENTIFIER && normalize(token.text) == "KHÔNG") {
                advance()
                return ScriptExpression.Unary("KHÔNG", parseExpression(7))
            }
            return when (token.type) {
                TokenType.NUMBER -> {
                    advance()
                    val value = token.text.toDoubleOrNull() ?: fail(token.line, token.column, "invalid_number", "Số không hợp lệ.", "Invalid number.")
                    if (!value.isFinite()) fail(token.line, token.column, "invalid_number", "Số phải hữu hạn.", "Numbers must be finite.")
                    ScriptExpression.NumberLiteral(value)
                }
                TokenType.STRING -> {
                    advance()
                    ScriptExpression.StringLiteral(token.text)
                }
                TokenType.IDENTIFIER -> parseIdentifierExpression()
                TokenType.SYMBOL -> {
                    if (!isSymbol("(")) failCurrent("expected_expression", "Cần một giá trị hoặc biểu thức.", "Expected a value or expression.")
                    advance()
                    val nested = parseExpression()
                    expectSymbol(")", "expected_right_parenthesis", "Thiếu dấu ')'.", "Expected ')'.")
                    nested
                }
                else -> failCurrent("expected_expression", "Cần một giá trị hoặc biểu thức.", "Expected a value or expression.")
            }
        }

        private fun parseIdentifierExpression(): ScriptExpression {
            val token = advance()
            return when (normalize(token.text)) {
                "ĐÚNG" -> ScriptExpression.BooleanLiteral(true)
                "SAI" -> ScriptExpression.BooleanLiteral(false)
                else -> {
                    if (isSymbol("(")) {
                        advance()
                        val args = mutableListOf<ScriptExpression>()
                        if (!isSymbol(")")) {
                            while (true) {
                                args += parseExpression()
                                if (!isSymbol(",")) break
                                advance()
                            }
                        }
                        expectSymbol(")", "expected_right_parenthesis", "Thiếu dấu ')' đóng hàm.", "Expected ')' after function arguments.")
                        ScriptExpression.FunctionCall(normalize(token.text), args)
                    } else ScriptExpression.Variable(token.text)
                }
            }
        }

        private fun currentOperator(): String? {
            val token = current()
            if (token.type == TokenType.SYMBOL && token.text in setOf("+", "-", "*", "/", "%", "==", "!=", ">", ">=", "<", "<=")) return token.text
            if (token.type == TokenType.IDENTIFIER) return when (normalize(token.text)) {
                "VÀ" -> "VÀ"
                "HOẶC" -> "HOẶC"
                else -> null
            }
            return null
        }

        private fun precedence(operator: String): Int = when (operator) {
            "HOẶC" -> 1
            "VÀ" -> 2
            "==", "!=" -> 3
            ">", ">=", "<", "<=" -> 4
            "+", "-" -> 5
            "*", "/", "%" -> 6
            else -> -1
        }

        private fun optionalEmptyCall() {
            if (!isSymbol("(")) return
            advance()
            expectSymbol(")", "unexpected_arguments", "Lệnh điều khiển không nhận tham số.", "This control statement takes no arguments.")
        }

        private fun requireInsideLoop(name: Token, loopDepth: Int) {
            if (loopDepth <= 0) fail(name.line, name.column, "loop_control_outside_loop", "Lệnh điều khiển vòng lặp chỉ dùng bên trong vòng lặp.", "Loop control can only be used inside a loop.")
        }

        private fun requireNesting(name: Token, depth: Int) {
            if (depth >= MAX_NESTING_DEPTH) fail(name.line, name.column, "nesting_limit", "Khối lệnh chỉ được lồng tối đa $MAX_NESTING_DEPTH cấp.", "Block nesting is limited to $MAX_NESTING_DEPTH levels.")
        }

        private fun skipSeparators() {
            while (current().type == TokenType.NEWLINE || current().type == TokenType.SEMICOLON) advance()
        }

        private fun expect(type: TokenType, code: String, messageVi: String, messageEn: String): Token {
            if (current().type != type) failCurrent(code, messageVi, messageEn)
            return advance()
        }

        private fun expectSymbol(symbol: String, code: String, messageVi: String, messageEn: String): Token {
            if (!isSymbol(symbol)) failCurrent(code, messageVi, messageEn)
            return advance()
        }

        private fun isSymbol(symbol: String): Boolean = current().type == TokenType.SYMBOL && current().text == symbol
        private fun isIdentifier(identifier: String): Boolean = current().type == TokenType.IDENTIFIER && normalize(current().text) == identifier
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

    private fun normalize(value: String): String = value.uppercase(Locale.ROOT)

    private fun failure(line: Int, column: Int, code: String, messageVi: String, messageEn: String) =
        ScriptCompileResult(null, listOf(ScriptDiagnostic(line, column, code, messageVi, messageEn)))

    private fun fail(line: Int, column: Int, code: String, messageVi: String, messageEn: String): Nothing {
        throw CompileFailure(ScriptDiagnostic(line, column, code, messageVi, messageEn))
    }
}
