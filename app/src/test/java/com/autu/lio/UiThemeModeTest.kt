package com.autu.lio

import org.junit.Assert.assertEquals
import org.junit.Test

class UiThemeModeTest {

    @Test
    fun darkModeAlwaysUsesDarkPalette() {
        assertEquals(true, UiThemeMode.DARK.isDark(systemIsDark = false))
        assertEquals(true, UiThemeMode.DARK.isDark(systemIsDark = true))
    }

    @Test
    fun lightModeAlwaysUsesLightPalette() {
        assertEquals(false, UiThemeMode.LIGHT.isDark(systemIsDark = false))
        assertEquals(false, UiThemeMode.LIGHT.isDark(systemIsDark = true))
    }

    @Test
    fun systemModeFollowsAndroidAppearance() {
        assertEquals(false, UiThemeMode.SYSTEM.isDark(systemIsDark = false))
        assertEquals(true, UiThemeMode.SYSTEM.isDark(systemIsDark = true))
    }

    @Test
    fun unknownPreferenceDefaultsToSystem() {
        assertEquals(UiThemeMode.SYSTEM, UiThemeMode.fromPreference(null))
        assertEquals(UiThemeMode.SYSTEM, UiThemeMode.fromPreference("unknown"))
    }

    @Test
    fun knownPreferencesRestoreTheirModes() {
        assertEquals(UiThemeMode.DARK, UiThemeMode.fromPreference("dark"))
        assertEquals(UiThemeMode.LIGHT, UiThemeMode.fromPreference("light"))
        assertEquals(UiThemeMode.SYSTEM, UiThemeMode.fromPreference("system"))
    }
}
