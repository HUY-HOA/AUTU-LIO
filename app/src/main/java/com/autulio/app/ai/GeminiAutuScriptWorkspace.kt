package com.autulio.app.ai

import android.content.Context
import com.autulio.app.SecureScriptDraftStore
import com.autulio.app.script.ScriptCompiler
import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrow local bridge for Gemini Live. It can only work with AUTU script drafts;
 * it has no filesystem, Android source, build-system, publishing, or execution API.
 */
internal class GeminiAutuScriptWorkspace(context: Context) {
    private val store = SecureScriptDraftStore(context.applicationContext, "admin")

    fun executeTool(name: String, args: JSONObject): JSONObject = try {
        when (name) {
            "get_autu_script_reference" -> referenceResult()
            "list_autu_script_drafts" -> listDrafts()
            "read_autu_script_draft" -> readDraft(args)
            "validate_autu_script" -> validate(args)
            "save_autu_script_draft" -> save(args)
            else -> failure("unsupported_tool", "This AUTU script tool is not supported.")
        }
    } catch (exception: Exception) {
        failure("tool_failed", exception.message?.take(180) ?: "The AUTU draft operation failed.")
    }

    private fun referenceResult(): JSONObject = JSONObject()
        .put("ok", true)
        .put("languageVersion", ScriptCompiler.LANGUAGE_VERSION)
        .put("reference", AUTU_REFERENCE)

    private fun listDrafts(): JSONObject {
        val snapshot = store.load()
        val names = JSONArray()
        snapshot.names.take(MAX_DRAFTS).forEach(names::put)
        return JSONObject()
            .put("ok", true)
            .put("count", snapshot.names.size)
            .put("truncated", snapshot.names.size > MAX_DRAFTS)
            .put("names", names)
            .put("storage", "encrypted_local_device")
    }

    private fun readDraft(args: JSONObject): JSONObject {
        val requestedName = requireString(args, "name", MAX_NAME_LENGTH).trim()
        requireValidName(requestedName)
        val snapshot = store.load()
        val name = snapshot.names.firstOrNull { it.equals(requestedName, ignoreCase = true) }
            ?: return failure("draft_not_found", "No AUTU draft with that name exists.")
        val source = snapshot.contents[name]
            ?: return failure("draft_content_missing", "The AUTU draft content could not be found.")
        return JSONObject()
            .put("ok", true)
            .put("name", name)
            .put("languageVersion", ScriptCompiler.LANGUAGE_VERSION)
            .put("source", source)
    }

    private fun validate(args: JSONObject): JSONObject {
        val source = requireString(args, "source", ScriptCompiler.MAX_SOURCE_LENGTH)
        if (source.isBlank()) return failure("empty_source", "AUTU script source is empty.")
        return compileResult(source)
    }

    private fun save(args: JSONObject): JSONObject {
        val requestedName = requireString(args, "name", MAX_NAME_LENGTH).trim()
        requireValidName(requestedName)
        val source = requireString(args, "source", ScriptCompiler.MAX_SOURCE_LENGTH)
        if (source.isBlank()) return failure("empty_source", "AUTU script source is empty.")

        val compilation = ScriptCompiler.compile(source)
        if (!compilation.isSuccess) return compileResult(source, compilation)

        val snapshot = store.load()
        val existingName = snapshot.names.firstOrNull { it.equals(requestedName, ignoreCase = true) }
        val savedName = existingName ?: requestedName
        if (existingName == null && snapshot.names.size >= MAX_DRAFTS) {
            return failure("draft_limit_reached", "The local AUTU draft limit has been reached.")
        }

        val names = if (existingName == null) snapshot.names + savedName else snapshot.names
        val contents = snapshot.contents.toMutableMap().apply { put(savedName, source) }
        store.save(names, contents)

        val program = compilation.program!!
        return JSONObject()
            .put("ok", true)
            .put("saved", true)
            .put("name", savedName)
            .put("languageVersion", program.languageVersion)
            .put("sourceCharacters", source.length)
            .put("expandedInstructions", program.expandedInstructionCount)
            .put("estimatedDurationMs", program.estimatedDurationMs)
            .put("storage", "encrypted_local_device")
            .put("executed", false)
            .put("published", false)
    }

    private fun compileResult(
        source: String,
        existing: com.autulio.app.script.ScriptCompileResult? = null
    ): JSONObject {
        val compilation = existing ?: ScriptCompiler.compile(source)
        val diagnostics = JSONArray()
        compilation.diagnostics.take(MAX_DIAGNOSTICS).forEach { diagnostic ->
            diagnostics.put(
                JSONObject()
                    .put("line", diagnostic.line)
                    .put("column", diagnostic.column)
                    .put("code", diagnostic.code)
                    .put("message", diagnostic.messageVi)
            )
        }
        val program = compilation.program
        return JSONObject()
            .put("ok", compilation.isSuccess)
            .put("valid", compilation.isSuccess)
            .put("languageVersion", ScriptCompiler.LANGUAGE_VERSION)
            .put("diagnosticCount", compilation.diagnostics.size)
            .put("diagnostics", diagnostics)
            .put("diagnosticsTruncated", compilation.diagnostics.size > MAX_DIAGNOSTICS)
            .apply {
                if (program != null) {
                    put("expandedInstructions", program.expandedInstructionCount)
                    put("estimatedDurationMs", program.estimatedDurationMs)
                }
            }
    }

    private fun requireString(args: JSONObject, key: String, maxLength: Int): String {
        if (!args.has(key) || args.isNull(key)) {
            throw IllegalArgumentException("Missing $key.")
        }
        val value = args.get(key) as? String
            ?: throw IllegalArgumentException("$key must be a string.")
        if (value.length > maxLength) throw IllegalArgumentException("$key is too long.")
        return value
    }

    private fun requireValidName(name: String) {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH || name.any(Char::isISOControl)) {
            throw IllegalArgumentException("Draft name must contain 1 to $MAX_NAME_LENGTH printable characters.")
        }
    }

    private fun failure(code: String, message: String): JSONObject =
        JSONObject().put("ok", false).put("error", code).put("message", message)

    private companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_DRAFTS = 100
        const val MAX_DIAGNOSTICS = 20

        const val AUTU_REFERENCE = """
AUTU-LIO Script 1.19 quick reference.
Only produce AUTU Script source. Keywords, function names, types and user variable names must be uppercase; Vietnamese words must keep their full diacritics.
Types: SỐ, CHUỖI, LUẬN_LÝ, DANH_SÁCH, BẢN_ĐỒ, BẤT_KỲ. Boolean literals: ĐÚNG, SAI. Null: RỖNG.
Variables: ĐẶT SO_LAN: SỐ = 3. Assignment: SO_LAN = SO_LAN + 1.
Conditions: NẾU (PIN() > 20 VÀ CÓ_MẠNG()) { CHẠM(200, 400) } NGƯỢC_LẠI { CHỜ(500) }.
Loops: LẶP(3) { CHẠM(200, 400) }; LẶP_KHI (SO_LAN < 5) { SO_LAN = SO_LAN + 1 }; LẶP_MỖI (X TRONG TẠO_DANH_SÁCH(100, 200)) { CHẠM(X, 400) }.
Actions: CHẠM(X,Y), NHẤN_GIỮ(X,Y,MS), VUỐT(X1,Y1,X2,Y2,MS), CHỜ(MS), MỞ_ỨNG_DỤNG("package.name"), QUAY_LẠI().
Multi-touch: CHẠM_ĐA_ĐIỂM(PATHS,MS), VUỐT_ĐA_ĐIỂM(PATHS,MS).
Functions may be declared at top level: ĐỊNH_NGHĨA_HÀM TEN(A: SỐ): SỐ { TRẢ_VỀ A + 1 }.
Control keywords also include NGƯỢC_LẠI_NẾU, LẶP_ĐẾN_KHI, CHỜ_ĐẾN_KHI, THOÁT_LẶP, BỎ_QUA_LƯỢT, THỬ, BẮT_LỖI and LUÔN_THỰC_HIỆN when supported by the current parser.
Use only documented built-ins. Common examples: SỐ, CHUỖI, ĐỘ_DÀI, CHỨA_CHUỖI, TẠO_DANH_SÁCH, LẤY_PHẦN_TỬ, TẠO_BẢN_ĐỒ, LẤY_GIÁ_TRỊ, PHÂN_TÍCH_JSON, CHUYỂN_JSON, PIN, PHẦN_TRĂM_PIN, ĐANG_SẠC, CÓ_MẠNG, MÀN_HÌNH_SÁNG, ĐỌC_VĂN_BẢN_MÀN_HÌNH, TÌM_VỊ_TRÍ_CHỮ.
Limits: source <= 32768 characters, max 100 drafts, max 5000 expanded instructions and 10 minutes estimated runtime per script. Compile before saving. Correct every compiler diagnostic. Save only AUTU source; never produce Kotlin, Java, Gradle, XML, Worker code, SQL migrations, app settings or APK commands.
A saved draft is never run or published by these tools. Tell the user to review it in the AUTU script editor and choose Run manually.
"""
    }
}
