package com.autu.lio

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
        assertEquals(30, apiFor("MÀU_TẠI"))
        assertEquals(28, apiFor("CÓ_THỂ_XÁC_ĐỊNH_VÙNG_KHUYẾT_MÀN_HÌNH"))
    }

    @Test
    fun accessibilityTextFallbackDocumentsPartialSupportOnOlderAndroid() {
        val command = EditorGuideCatalog.sections
            .flatMap { it.commands }
            .first { it.syntax.startsWith("MÀN_HÌNH_CHỨA_CHỮ(") }

        assertEquals(26, command.minAndroidApi)
        assertTrue(command.androidNote.orEmpty().contains("Android 11.0 (API 30)"))
    }

    @Test
    fun templatesExposeUniqueNamesAndValidSource() {
        val identifiers = com.autu.lio.script.ScriptTemplateCatalog.templates.map { it.id }
        assertEquals(identifiers.size, identifiers.distinct().size)
        com.autu.lio.script.ScriptTemplateCatalog.templates.forEach { template ->
            assertTrue(template.nameVi.isNotBlank())
            assertTrue(template.descriptionVi.isNotBlank())
            assertTrue(template.source.isNotBlank())
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

        assertTrue(com.autu.lio.script.ScriptCompiler.compile(source).isSuccess)
    }

}
