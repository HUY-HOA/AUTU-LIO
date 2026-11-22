package com.autulio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGuideCatalogTest {
    @Test
    fun guideSectionIdentifiersAreUnique() {
        val identifiers = EditorGuideCatalog.sections.map { it.id }
        assertEquals(identifiers.size, identifiers.distinct().size)
    }

    @Test
    fun everyGuideSectionHasVietnameseTitleSummaryAndCommands() {
        assertTrue(EditorGuideCatalog.sections.isNotEmpty())
        EditorGuideCatalog.sections.forEach { section ->
            assertTrue(section.title.isNotBlank())
            assertTrue(section.summary.isNotBlank())
            assertFalse(section.commands.isEmpty())
            section.commands.forEach { command ->
                assertTrue(command.syntax.isNotBlank())
                assertTrue(command.description.isNotBlank())
            }
        }
    }

    @Test
    fun everyCommandDeclaresSupportedAndroidMinimum() {
        EditorGuideCatalog.sections.flatMap { it.commands }.forEach { command ->
            assertTrue(command.minAndroidApi >= 26)
            assertTrue(command.androidRequirement.isNotBlank())
        }
    }

    @Test
    fun elevatedAndroidRequirementsMatchRuntimeGuards() {
        fun apiFor(prefix: String) = EditorGuideCatalog.sections
            .flatMap { it.commands }
            .first { it.syntax.startsWith(prefix) }
            .minAndroidApi

        assertEquals(28, apiFor("KHÓA_MÀN_HÌNH"))
        assertEquals(30, apiFor("BẬT_TẮT_CHIA_MÀN_HÌNH"))
        assertEquals(30, apiFor("ĐỌC_VĂN_BẢN_MÀN_HÌNH"))
        assertEquals(30, apiFor("NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH"))
        assertEquals(30, apiFor("LẤY_MÀU_TẠI_TỌA_ĐỘ_MÀN_HÌNH"))
        assertEquals(28, apiFor("KIỂM_TRA_KHẢ_NĂNG_XÁC_ĐỊNH_VÙNG_KHUYẾT_MÀN_HÌNH"))
    }

    @Test
    fun accessibilityTextFallbackDocumentsPartialSupportOnOlderAndroid() {
        val command = EditorGuideCatalog.sections
            .flatMap { it.commands }
            .first { it.syntax.startsWith("KIỂM_TRA_CHỮ_TRÊN_MÀN_HÌNH(") }

        assertEquals(26, command.minAndroidApi)
        assertTrue(command.androidNote.orEmpty().contains("Android 11.0 (API 30)"))
    }

    @Test
    fun templatesExposeUniqueNamesAndValidSource() {
        val identifiers = com.autulio.app.script.ScriptTemplateCatalog.templates.map { it.id }
        assertEquals(identifiers.size, identifiers.distinct().size)
        com.autulio.app.script.ScriptTemplateCatalog.templates.forEach { template ->
            assertTrue(template.nameVi.isNotBlank())
            assertTrue(template.descriptionVi.isNotBlank())
            assertTrue(template.source.isNotBlank())
            val compiled = com.autulio.app.script.ScriptCompiler.compile(template.source)
            assertTrue(
                "Mẫu ${template.id} không biên dịch được: ${compiled.diagnostics}",
                compiled.isSuccess
            )
        }
    }

    @Test
    fun screenAnnotationExamplesCompile() {
        val source = """
            HIỂN_THỊ_CHỮ("ĐANG KIỂM TRA", 80, 180, "#FFFF3B30", 32)
            VẼ_KHUNG(40, 210, 300, 120, "#FF30D158", 4)
            VẼ_HÌNH_TRÒN(200, 300, 45, "#FFFFC107", 4)
            VẼ_ĐƯỜNG(40, 270, 340, 270, "#FF0A84FF", 3)
            VẼ_MŨI_TÊN(40, 270, 340, 270, "#FF0A84FF", 4)
            XÓA_ĐÁNH_DẤU_MÀN_HÌNH()
        """.trimIndent()

        assertTrue(com.autulio.app.script.ScriptCompiler.compile(source).isSuccess)
    }

    @Test
    fun guideSectionsRemainInNumericOrderAfterCommandExpansion() {
        val numbers = EditorGuideCatalog.sections.map { section ->
            Regex("""^(\d+)\.""").find(section.title)?.groupValues?.get(1)?.toInt()
        }
        assertTrue(numbers.all { it != null })
        assertEquals((1..EditorGuideCatalog.sections.size).toList(), numbers.filterNotNull())
    }

}
