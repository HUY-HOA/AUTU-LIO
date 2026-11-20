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
    fun templatesExposeUniqueNamesAndValidSource() {
        val identifiers = com.autulio.app.script.ScriptTemplateCatalog.templates.map { it.id }
        assertEquals(identifiers.size, identifiers.distinct().size)
        com.autulio.app.script.ScriptTemplateCatalog.templates.forEach { template ->
            assertTrue(template.nameVi.isNotBlank())
            assertTrue(template.descriptionVi.isNotBlank())
            assertTrue(template.source.isNotBlank())
        }
    }
}
