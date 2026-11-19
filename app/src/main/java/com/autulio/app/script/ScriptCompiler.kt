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

data class ScriptFunctionParameter(val name: String, val declaredType: String?)

sealed interface ScriptInstruction {
    data class FunctionDefinition(
        val name: String,
        val parameters: List<ScriptFunctionParameter>,
        val returnType: String?,
        val body: List<ScriptInstruction>
    ) : ScriptInstruction
    data class Return(val value: ScriptExpression) : ScriptInstruction
    data class Command(val name: String, val arguments: List<ScriptExpression>) : ScriptInstruction
    data class SetVariable(
        val name: String,
        val value: ScriptExpression,
        val declaredType: String? = null
    ) : ScriptInstruction
    data class If(val condition: ScriptExpression, val thenBody: List<ScriptInstruction>, val elseBody: List<ScriptInstruction>) : ScriptInstruction
    data class Repeat(val count: ScriptExpression, val body: List<ScriptInstruction>) : ScriptInstruction
    data class While(val condition: ScriptExpression, val body: List<ScriptInstruction>) : ScriptInstruction
    data class WaitUntil(
        val condition: ScriptExpression,
        val timeoutMs: ScriptExpression,
        val intervalMs: ScriptExpression
    ) : ScriptInstruction
    data class ForEach(
        val variableName: String,
        val iterable: ScriptExpression,
        val body: List<ScriptInstruction>
    ) : ScriptInstruction
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
    const val LANGUAGE_VERSION = "1.3"
    const val MAX_SOURCE_LENGTH = 32_768
    const val MAX_STATEMENTS = 1_000
    const val MAX_REPEAT_COUNT = 100
    const val MAX_WHILE_ITERATIONS = 900
    const val MAX_LIST_ITEMS = 64
    const val MAX_MAP_ENTRIES = 64
    const val MAX_NESTING_DEPTH = 8
    const val MAX_EXPANDED_INSTRUCTIONS = 5_000L
    const val MAX_TOTAL_DURATION_MS = 600_000L
    const val MAX_COORDINATE = 10_000
    const val MAX_WAIT_MS = 60_000
    const val MAX_GESTURE_MS = 10_000
    private const val INSTRUCTION_CAP = MAX_EXPANDED_INSTRUCTIONS + 1
    private const val DURATION_CAP = MAX_TOTAL_DURATION_MS + 1

    // AUTU Script 1.0 chỉ chấp nhận lệnh tiếng Việt viết HOA, đúng dấu.
    private val commandAliases = mapOf(
        "CHẠM" to "CHẠM",
        "NHẤN_GIỮ" to "NHẤN_GIỮ",
        "VUỐT" to "VUỐT",
        "CHỜ" to "CHỜ",
        "MỞ_ỨNG_DỤNG" to "MỞ_ỨNG_DỤNG",
        "QUAY_LẠI" to "QUAY_LẠI",
        "VỀ_MÀN_HÌNH_CHÍNH" to "VỀ_MÀN_HÌNH_CHÍNH",
        "ỨNG_DỤNG_GẦN_ĐÂY" to "ỨNG_DỤNG_GẦN_ĐÂY",
        "MỞ_THÔNG_BÁO" to "MỞ_THÔNG_BÁO",
        "MỞ_CÀI_ĐẶT_NHANH" to "MỞ_CÀI_ĐẶT_NHANH",
        "HIỆN_MENU_NGUỒN" to "HIỆN_MENU_NGUỒN",
        "KHÓA_MÀN_HÌNH" to "KHÓA_MÀN_HÌNH",
        "CHIA_MÀN_HÌNH" to "CHIA_MÀN_HÌNH"
    )

    private val commandArgumentCounts = mapOf(
        "CHẠM" to 2, "NHẤN_GIỮ" to 3, "VUỐT" to 5,
        "CHỜ" to 1, "MỞ_ỨNG_DỤNG" to 1,
        "QUAY_LẠI" to 0, "VỀ_MÀN_HÌNH_CHÍNH" to 0,
        "ỨNG_DỤNG_GẦN_ĐÂY" to 0, "MỞ_THÔNG_BÁO" to 0,
        "MỞ_CÀI_ĐẶT_NHANH" to 0, "HIỆN_MENU_NGUỒN" to 0,
        "KHÓA_MÀN_HÌNH" to 0, "CHIA_MÀN_HÌNH" to 0
    )

    private val builtinFunctionArities = mapOf(
        "SỐ" to 1, "CHUỖI" to 1, "LÀM_TRÒN" to 1,
        "TUYỆT_ĐỐI" to 1, "ĐỘ_DÀI" to 1,
        "CHỮ_HOA" to 1, "CHỮ_THƯỜNG" to 1, "CẮT_KHOẢNG_TRẮNG" to 1,
        "CHỨA_CHUỖI" to 2, "BẮT_ĐẦU_BẰNG" to 2, "KẾT_THÚC_BẰNG" to 2,
        "THAY_THẾ" to 3, "TÁCH_CHUỖI" to 2, "NỐI_CHUỖI" to 2,
        "KÝ_TỰ_TẠI" to 2, "CẮT_CHUỖI" to 3,
        "NHỎ_NHẤT" to 2, "LỚN_NHẤT" to 2,
        "PIN" to 0, "PHẦN_TRĂM_PIN" to 0,
        "ĐANG_SẠC" to 0, "CÓ_MẠNG" to 0, "MÀN_HÌNH_SÁNG" to 0,
        "ỨNG_DỤNG_ĐÃ_CÀI" to 1, "ỨNG_DỤNG_ĐANG_MỞ" to 1,
        "CHIỀU_RỘNG_MÀN_HÌNH" to 0, "CHIỀU_CAO_MÀN_HÌNH" to 0,
        "THỜI_GIAN_MS" to 0,
        "TẠO_DANH_SÁCH" to -1,
        "LẤY_PHẦN_TỬ" to 2,
        "THÊM_PHẦN_TỬ" to 2,
        "ĐẶT_PHẦN_TỬ" to 3,
        "XÓA_PHẦN_TỬ" to 2,
        "CÓ_PHẦN_TỬ" to 2,
        "TẠO_BẢN_ĐỒ" to -2,
        "LẤY_GIÁ_TRỊ" to 2,
        "ĐẶT_GIÁ_TRỊ" to 3,
        "XÓA_KHÓA" to 2,
        "CÓ_KHÓA" to 2,
        "DANH_SÁCH_KHÓA" to 1
    )

    private val supportedTypes = setOf("SỐ", "CHUỖI", "LUẬN_LÝ", "DANH_SÁCH", "BẢN_ĐỒ", "BẤT_KỲ")
    private val reservedWords = setOf(
        "ĐẶT", "NẾU", "NGƯỢC_LẠI_NẾU", "NGƯỢC_LẠI",
        "LẶP", "LẶP_KHI", "LẶP_ĐẾN_KHI", "LẶP_MỖI", "TRONG", "CHỜ_ĐẾN_KHI",
        "THOÁT_LẶP", "BỎ_QUA_LƯỢT", "ĐỊNH_NGHĨA_HÀM", "TRẢ_VỀ",
        "ĐÚNG", "SAI", "VÀ", "HOẶC", "KHÔNG"
    )

    fun compile(source: String): ScriptCompileResult {
        if (source.length > MAX_SOURCE_LENGTH) {
            return failure(1, 1, "source_too_large", "Mã vượt quá giới hạn 32.768 ký tự.", "Source exceeds the 32,768-character limit.")
        }
        return try {
            val instructions = Parser(source).parse()
            if (instructions.none { it !is ScriptInstruction.FunctionDefinition }) {
                return failure(1, 1, "empty_program", "Chương trình cần có ít nhất một lệnh chạy ngoài phần định nghĩa hàm.", "The program needs at least one executable instruction outside function definitions.")
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
                is ScriptInstruction.FunctionDefinition -> {
                    val body = measureBlock(instruction.body, depth + 1)
                    cappedAdd(1L, body.first, INSTRUCTION_CAP) to body.second
                }
                is ScriptInstruction.Return -> 1L to 0L
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
                is ScriptInstruction.WaitUntil -> 1L to expressionUpperBound(instruction.timeoutMs, MAX_WAIT_MS.toLong())
                is ScriptInstruction.ForEach -> {
                    val nested = measureBlock(instruction.body, depth + 1)
                    cappedAdd(1L, cappedMultiply(nested.first, MAX_LIST_ITEMS.toLong(), INSTRUCTION_CAP), INSTRUCTION_CAP) to
                        cappedMultiply(nested.second, MAX_LIST_ITEMS.toLong(), DURATION_CAP)
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
                    "^" -> Math.pow(left, right)
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
            if (current in "(){} ,;+-*/%=<>^:") {
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
        private var insideFunction = false
        private val functionNames = linkedSetOf<String>()
        private val functionCallTokens = mutableListOf<Token>()

        fun parse(): List<ScriptInstruction> {
            val instructions = parseBlock(false, 0, 0)
            val unknown = functionCallTokens.firstOrNull { it.text !in functionNames }
            if (unknown != null) {
                fail(unknown.line, unknown.column, "unknown_function", "Không tìm thấy hàm '" + unknown.text + "'. Hãy định nghĩa hàm bằng ĐỊNH_NGHĨA_HÀM trước khi dùng.", "Function '" + unknown.text + "' is not defined. Define it with ĐỊNH_NGHĨA_HÀM before use.")
            }
            return instructions
        }

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
            requireUppercaseIdentifier(name, "lệnh hoặc tên biến")
            val keyword = name.text
            return when (keyword) {
                "ĐẶT" -> parseVariableDeclaration(name)
                "NẾU" -> parseIf(name, depth, loopDepth)
                "LẶP" -> parseRepeat(name, depth, loopDepth)
                "LẶP_KHI" -> parseWhile(name, depth, loopDepth)
                "LẶP_ĐẾN_KHI" -> parseUntil(name, depth, loopDepth)
                "LẶP_MỖI" -> parseForEach(name, depth, loopDepth)
                "CHỜ_ĐẾN_KHI" -> parseWaitUntil(name)
                "ĐỊNH_NGHĨA_HÀM" -> parseFunctionDefinition(name, depth, loopDepth)
                "TRẢ_VỀ" -> {
                    if (!insideFunction) fail(name.line, name.column, "return_outside_function", "TRẢ_VỀ chỉ được dùng bên trong hàm.", "TRẢ_VỀ can only be used inside a function.")
                    ScriptInstruction.Return(parseExpression())
                }
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
                    if (name.text in reservedWords) {
                        fail(name.line, name.column, "keyword_usage", "Từ khóa '" + name.text + "' không được dùng ở vị trí này.", "Keyword '" + name.text + "' is not valid here.")
                    }
                    if (isSymbol("=")) {
                        advance()
                        ScriptInstruction.SetVariable(name.text, parseExpression())
                    } else parseCommand(name)
                }
            }
        }

        private fun parseFunctionDefinition(name: Token, depth: Int, loopDepth: Int): ScriptInstruction.FunctionDefinition {
            if (depth != 0 || loopDepth != 0 || insideFunction) {
                fail(name.line, name.column, "nested_function", "ĐỊNH_NGHĨA_HÀM chỉ được đặt ở cấp cao nhất, không đặt bên trong NẾU hoặc vòng lặp.", "ĐỊNH_NGHĨA_HÀM must be declared at the top level, not inside NẾU or a loop.")
            }
            val functionName = expect(TokenType.IDENTIFIER, "expected_function_name", "Sau ĐỊNH_NGHĨA_HÀM cần có tên hàm viết HOA.", "Expected an uppercase function name after ĐỊNH_NGHĨA_HÀM.")
            requireUppercaseIdentifier(functionName, "tên hàm")
            if (functionName.text in reservedWords || functionName.text in commandAliases || functionName.text in builtinFunctionArities) {
                fail(functionName.line, functionName.column, "reserved_function_name", "Tên hàm bị trùng với từ khóa, lệnh hoặc hàm dựng sẵn.", "Function name conflicts with a keyword, command or built-in function.")
            }
            if (!functionNames.add(functionName.text)) {
                fail(functionName.line, functionName.column, "duplicate_function", "Hàm '" + functionName.text + "' đã được định nghĩa.", "Function '" + functionName.text + "' is already defined.")
            }

            expectSymbol("(", "expected_left_parenthesis", "Thiếu '(' sau tên hàm.", "Expected '(' after function name.")
            val parameters = mutableListOf<ScriptFunctionParameter>()
            val parameterNames = mutableSetOf<String>()
            if (!isSymbol(")")) {
                while (true) {
                    val parameter = expect(TokenType.IDENTIFIER, "expected_parameter_name", "Tham số hàm phải có tên viết HOA.", "Function parameters must have uppercase names.")
                    requireUppercaseIdentifier(parameter, "tên tham số")
                    if (parameter.text in reservedWords || parameter.text in commandAliases || builtinFunctionArities.containsKey(parameter.text)) {
                        fail(parameter.line, parameter.column, "reserved_parameter_name", "Tên tham số bị trùng với từ khóa, lệnh hoặc hàm dựng sẵn.", "Parameter name conflicts with a keyword, command or built-in function.")
                    }
                    if (!parameterNames.add(parameter.text)) {
                        fail(parameter.line, parameter.column, "duplicate_parameter", "Tham số '" + parameter.text + "' bị lặp.", "Parameter '" + parameter.text + "' is duplicated.")
                    }
                    val parameterType = if (isSymbol(":")) {
                        advance()
                        val type = expect(TokenType.IDENTIFIER, "expected_parameter_type", "Thiếu kiểu tham số.", "Missing parameter type.")
                        if (type.text !in supportedTypes) {
                            fail(type.line, type.column, "unknown_type", "Kiểu tham số '" + type.text + "' chưa được hỗ trợ.", "Parameter type '" + type.text + "' is not supported.")
                        }
                        type.text
                    } else null
                    parameters += ScriptFunctionParameter(parameter.text, parameterType)
                    if (parameters.size > 16) {
                        fail(parameter.line, parameter.column, "too_many_parameters", "Mỗi hàm chỉ được có tối đa 16 tham số.", "A function may have at most 16 parameters.")
                    }
                    if (!isSymbol(",")) break
                    advance()
                }
            }
            expectSymbol(")", "expected_right_parenthesis", "Thiếu ')' sau danh sách tham số.", "Expected ')' after parameter list.")
            val returnType = if (isSymbol(":")) {
                advance()
                val type = expect(TokenType.IDENTIFIER, "expected_return_type", "Thiếu kiểu trả về của hàm.", "Missing function return type.")
                if (type.text !in supportedTypes) {
                    fail(type.line, type.column, "unknown_return_type", "Kiểu trả về '" + type.text + "' chưa được hỗ trợ.", "Return type '" + type.text + "' is not supported.")
                }
                type.text
            } else null

            insideFunction = true
            val body = try {
                parseBraceBlock(depth + 1, 0)
            } finally {
                insideFunction = false
            }
            if (body.isEmpty()) {
                fail(name.line, name.column, "empty_function", "Thân hàm không được để trống.", "A function body cannot be empty.")
            }
            return ScriptInstruction.FunctionDefinition(functionName.text, parameters, returnType, body)
        }

        private fun parseVariableDeclaration(name: Token): ScriptInstruction.SetVariable {
            val variable = expect(TokenType.IDENTIFIER, "expected_variable_name", "Sau ĐẶT cần có tên biến viết HOA.", "Expected an uppercase variable name after ĐẶT.")
            requireUppercaseIdentifier(variable, "tên biến")
            if (variable.text in reservedWords || variable.text in commandAliases) {
                fail(variable.line, variable.column, "reserved_identifier", "Tên biến trùng với từ khóa hoặc lệnh đã dành riêng.", "Variable name is reserved.")
            }
            val declaredType = if (isSymbol(":")) {
                advance()
                val typeToken = expect(TokenType.IDENTIFIER, "expected_type", "Sau ':' cần có kiểu SỐ, CHUỖI, LUẬN_LÝ hoặc BẤT_KỲ.", "Expected SỐ, CHUỖI, LUẬN_LÝ or BẤT_KỲ after ':'.")
                if (typeToken.text !in supportedTypes) {
                    fail(typeToken.line, typeToken.column, "unknown_type", "Kiểu '" + typeToken.text + "' chưa được hỗ trợ.", "Type '" + typeToken.text + "' is not supported.")
                }
                typeToken.text
            } else null
            expectSymbol("=", "expected_assignment", "Thiếu dấu '=' khi gán biến.", "Expected '=' in variable assignment.")
            return ScriptInstruction.SetVariable(variable.text, parseExpression(), declaredType)
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

        private fun parseUntil(name: Token, depth: Int, loopDepth: Int): ScriptInstruction.While {
            requireNesting(name, depth)
            val condition = parseParenthesizedExpression()
            val body = parseBraceBlock(depth + 1, loopDepth + 1)
            if (body.isEmpty()) fail(name.line, name.column, "empty_until", "Khối LẶP_ĐẾN_KHI không được để trống.", "A LẶP_ĐẾN_KHI block cannot be empty.")
            return ScriptInstruction.While(ScriptExpression.Unary("KHÔNG", condition), body)
        }

        private fun parseForEach(name: Token, depth: Int, loopDepth: Int): ScriptInstruction.ForEach {
            requireNesting(name, depth)
            expectSymbol("(", "expected_left_parenthesis", "Thiếu '(' sau LẶP_MỖI.", "Expected '(' after LẶP_MỖI.")
            val variable = expect(TokenType.IDENTIFIER, "expected_loop_variable", "Sau LẶP_MỖI cần tên biến viết HOA.", "Expected an uppercase loop variable after LẶP_MỖI.")
            requireUppercaseIdentifier(variable, "tên biến lặp")
            if (variable.text in reservedWords || variable.text in commandAliases || builtinFunctionArities.containsKey(variable.text)) {
                fail(variable.line, variable.column, "reserved_loop_variable", "Tên biến lặp bị trùng với từ khóa, lệnh hoặc hàm dựng sẵn.", "Loop variable conflicts with a keyword, command or built-in function.")
            }
            val inToken = expect(TokenType.IDENTIFIER, "expected_in_keyword", "Cần từ khóa TRONG giữa biến và danh sách.", "Expected TRONG between the loop variable and list.")
            if (inToken.text != "TRONG") {
                fail(inToken.line, inToken.column, "expected_in_keyword", "Từ khóa phải là TRONG, viết HOA và đầy đủ dấu.", "The keyword must be TRONG in uppercase.")
            }
            val iterable = parseExpression()
            expectSymbol(")", "expected_right_parenthesis", "Thiếu ')' sau danh sách lặp.", "Expected ')' after the iterable.")
            val body = parseBraceBlock(depth + 1, loopDepth + 1)
            if (body.isEmpty()) fail(name.line, name.column, "empty_for_each", "Khối LẶP_MỖI không được để trống.", "A LẶP_MỖI block cannot be empty.")
            return ScriptInstruction.ForEach(variable.text, iterable, body)
        }

        private fun parseWaitUntil(name: Token): ScriptInstruction.WaitUntil {
            val arguments = parseArguments()
            if (arguments.size != 3) {
                fail(name.line, name.column, "wait_until_argument_count", "CHỜ_ĐẾN_KHI cần đúng 3 tham số: điều kiện, thời hạn và chu kỳ kiểm tra.", "CHỜ_ĐẾN_KHI requires exactly 3 arguments: condition, timeout and polling interval.")
            }
            validateStaticIntegerRange(
                name, arguments[1], 1, MAX_WAIT_MS,
                "wait_until_timeout_range",
                "Thời hạn CHỜ_ĐẾN_KHI phải từ 1 đến $MAX_WAIT_MS mili giây.",
                "The CHỜ_ĐẾN_KHI timeout must be between 1 and $MAX_WAIT_MS milliseconds."
            )
            validateStaticIntegerRange(
                name, arguments[2], 50, 5_000,
                "wait_until_interval_range",
                "Chu kỳ kiểm tra phải từ 50 đến 5.000 mili giây.",
                "The polling interval must be between 50 and 5,000 milliseconds."
            )
            return ScriptInstruction.WaitUntil(arguments[0], arguments[1], arguments[2])
        }

        private fun validateStaticIntegerRange(
            name: Token,
            expression: ScriptExpression,
            minimum: Int,
            maximum: Int,
            code: String,
            messageVi: String,
            messageEn: String
        ) {
            val value = constantNumber(expression) ?: return
            if (!value.isFinite() || value % 1.0 != 0.0 || value < minimum || value > maximum) {
                fail(name.line, name.column, code, messageVi, messageEn)
            }
        }

        private fun parseBraceBlock(depth: Int, loopDepth: Int): List<ScriptInstruction> {
            expectSymbol("{", "expected_left_brace", "Thiếu dấu '{' mở khối lệnh.", "Expected '{' to open a block.")
            val body = parseBlock(true, depth, loopDepth)
            expectSymbol("}", "expected_right_brace", "Thiếu dấu '}' đóng khối lệnh.", "Expected '}' to close the block.")
            return body
        }

        private fun parseCommand(name: Token): ScriptInstruction.Command {
            val command = commandAliases[name.text]
                ?: fail(name.line, name.column, "unknown_command", "Không nhận ra lệnh '" + name.text + "'. AUTU Script 1.0 chỉ nhận lệnh tiếng Việt viết HOA và đúng dấu.", "Unknown command '" + name.text + "'. AUTU Script 1.0 accepts only uppercase Vietnamese commands with correct diacritics.")
            val args = parseArguments()
            val expected = commandArgumentCounts[command]
                ?: fail(name.line, name.column, "unsupported_command", "Lệnh '$command' chưa được đăng ký tham số.", "Command '$command' has no registered argument signature.")
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
                val right = parseExpression(if (operator == "^") priority else priority + 1)
                left = ScriptExpression.Binary(operator, left, right)
            }
            return left
        }

        private fun parsePrefix(): ScriptExpression {
            val token = current()
            if (token.type == TokenType.SYMBOL && token.text in setOf("-", "+")) {
                advance()
                return ScriptExpression.Unary(token.text, parseExpression(7))
            }
            if (token.type == TokenType.IDENTIFIER && token.text == "KHÔNG") {
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
            if (token.text == "ĐÚNG") return ScriptExpression.BooleanLiteral(true)
            if (token.text == "SAI") return ScriptExpression.BooleanLiteral(false)
            requireUppercaseIdentifier(token, "tên biến hoặc hàm")
            if (token.text in reservedWords || token.text in commandAliases) {
                fail(token.line, token.column, "reserved_identifier", "Từ khóa hoặc lệnh không thể dùng làm biến/hàm.", "A keyword or command cannot be used as a variable/function.")
            }
            return if (isSymbol("(")) {
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
                val expected = builtinFunctionArities[token.text]
                if (expected == -1 && args.size > MAX_LIST_ITEMS) {
                    fail(token.line, token.column, "list_too_large", "Danh sách không được vượt quá $MAX_LIST_ITEMS phần tử.", "A list cannot exceed $MAX_LIST_ITEMS items.")
                } else if (expected == -2 && args.size > MAX_MAP_ENTRIES * 2) {
                    fail(token.line, token.column, "map_too_large", "Bản đồ không được vượt quá $MAX_MAP_ENTRIES cặp khóa–giá trị.", "A map cannot exceed $MAX_MAP_ENTRIES key-value pairs.")
                } else if (expected == -2 && args.size % 2 != 0) {
                    fail(token.line, token.column, "map_argument_count", "TẠO_BẢN_ĐỒ cần số tham số chẵn theo cặp KHÓA, GIÁ_TRỊ.", "TẠO_BẢN_ĐỒ requires an even number of KEY, VALUE arguments.")
                } else if (expected != null && expected >= 0 && args.size != expected) {
                    fail(token.line, token.column, "function_argument_count", "Hàm '" + token.text + "' cần đúng " + expected + " tham số.", "Function '" + token.text + "' requires exactly " + expected + " arguments.")
                }
                if (expected == null) functionCallTokens += token
                ScriptExpression.FunctionCall(token.text, args)
            } else ScriptExpression.Variable(token.text)
        }

        private fun currentOperator(): String? {
            val token = current()
            if (token.type == TokenType.SYMBOL && token.text in setOf("+", "-", "*", "/", "%", "^", "==", "!=", ">", ">=", "<", "<=")) return token.text
            if (token.type == TokenType.IDENTIFIER) return when (token.text) {
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
            "^" -> 7
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
        private fun isIdentifier(identifier: String): Boolean = current().type == TokenType.IDENTIFIER && current().text == identifier

        private fun requireUppercaseIdentifier(token: Token, description: String) {
            if (token.text != normalize(token.text)) {
                fail(
                    token.line,
                    token.column,
                    "identifier_must_be_uppercase",
                    "Tên " + description + " '" + token.text + "' phải viết HOA. Ví dụ: SO_LAN, PIN_MUC_TIEU.",
                    "The " + description + " '" + token.text + "' must be uppercase. Example: SO_LAN, PIN_MUC_TIEU."
                )
            }
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

    private fun normalize(value: String): String = value.uppercase(Locale.ROOT)

    private fun failure(line: Int, column: Int, code: String, messageVi: String, messageEn: String) =
        ScriptCompileResult(null, listOf(ScriptDiagnostic(line, column, code, messageVi, messageEn)))

    private fun fail(line: Int, column: Int, code: String, messageVi: String, messageEn: String): Nothing {
        throw CompileFailure(ScriptDiagnostic(line, column, code, messageVi, messageEn))
    }
}
