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
    fun systemCommandsHaveTheirOwnCategorySeparateFromGestures() {
        val system = EditorGuideCatalog.sections.first { it.id == "system" }
        val gestures = EditorGuideCatalog.sections.first { it.id == "gestures" }
        val systemNames = system.commands.map { it.syntax }

        assertTrue(systemNames.any { it.startsWith("QUAY_LẠI") })
        assertTrue(systemNames.any { it.startsWith("VỀ_MÀN_HÌNH_CHÍNH") })
        assertTrue(systemNames.any { it.startsWith("MỞ_THANH_THÔNG_BÁO") })
        assertFalse(gestures.commands.any { it.syntax.startsWith("QUAY_LẠI") })
        assertFalse(gestures.commands.any { it.syntax.startsWith("VỀ_MÀN_HÌNH_CHÍNH") })
    }

    @Test
    fun guideCommandEntriesAreSearchableAndNotDuplicated() {
        val entries = EditorGuideCatalog.sections.flatMap { section ->
            section.commands.map { section.id to it.syntax }
        }
        val syntax = entries.map { it.second }

        assertTrue(EditorGuideCatalog.sections.any { it.id == "ocr" })
        assertTrue(EditorGuideCatalog.sections.any { it.id == "database" })
        assertEquals(syntax.size, syntax.distinct().size)
    }

    @Test
    fun templatesExposeUniqueNamesAndValidSource() {
        val identifiers = com.autulio.app.script.ScriptTemplateCatalog.templates.map { it.id }
        assertEquals(identifiers.size, identifiers.distinct().size)
        com.autulio.app.script.ScriptTemplateCatalog.templates.forEach { template ->
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

        assertTrue(com.autulio.app.script.ScriptCompiler.compile(source).isSuccess)
    }

}
