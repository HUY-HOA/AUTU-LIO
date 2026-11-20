package com.autulio.app.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptFilePickerMimePolicyTest {
    @Test
    fun acceptsCommonMimeTypesAndSubtypeWildcard() {
        listOf(
            "text/csv",
            "application/json",
            "application/vnd.ms-excel",
            "image/png",
            "image/*"
        ).forEach { assertTrue("$it should be accepted", ScriptFilePickerMimePolicy.isValid(it)) }
    }

    @Test
    fun rejectsWildcardMajorTypeMalformedAndOversizedValues() {
        listOf(
            "",
            "*/*",
            "text",
            "/csv",
            "text/",
            "text/csv/extra",
            "text/csv\r\nX-Test: 1",
            "x".repeat(128)
        ).forEach { assertFalse("Should reject: $it", ScriptFilePickerMimePolicy.isValid(it)) }
    }
}
