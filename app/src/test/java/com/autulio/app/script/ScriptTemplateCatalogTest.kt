package com.autulio.app.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptTemplateCatalogTest {
    @Test
    fun everyBuiltInTemplateCompiles() {
        assertFalse(ScriptTemplateCatalog.templates.isEmpty())
        ScriptTemplateCatalog.templates.forEach { template ->
            val result = ScriptCompiler.compile(template.source)
            assertTrue(template.id + ": " + result.diagnostics, result.isSuccess)
        }
    }

    @Test
    fun templateIdsAreUniqueAndSourcesAreNonBlank() {
        val templates = ScriptTemplateCatalog.templates
        assertEquals(templates.size, templates.map { it.id }.distinct().size)
        assertTrue(templates.all { it.id.isNotBlank() && it.source.isNotBlank() })
    }
}
