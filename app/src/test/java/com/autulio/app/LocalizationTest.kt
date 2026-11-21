package com.autulio.app

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {

    @Test
    fun alwaysUsesVietnameseForEverySystemLanguage() {
        withDefaultLocale(Locale("vi", "VN")) {
            assertEquals("Xin chào", tr("Xin chào", "Hello"))
        }
        withDefaultLocale(Locale.US) {
            assertEquals("Xin chào", tr("Xin chào", "Hello"))
        }
        withDefaultLocale(Locale.JAPAN) {
            assertEquals("Xin chào", tr("Xin chào", "Hello"))
        }
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
