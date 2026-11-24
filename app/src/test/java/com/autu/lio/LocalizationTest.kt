package com.autu.lio

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {

    @Test
    fun alwaysUsesVietnameseForEverySystemLanguage() {
        val locales = listOf(
            Locale("vi", "VN"),
            Locale("vi"),
            Locale.US,
            Locale.UK,
            Locale.JAPAN,
            Locale.FRANCE
        )

        locales.forEach { locale ->
            withDefaultLocale(locale) {
                assertEquals("Xin chào", tr("Xin chào", "Hello"))
            }
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
