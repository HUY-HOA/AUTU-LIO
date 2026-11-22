package com.autulio.app

import android.content.Context
import androidx.compose.runtime.compositionLocalOf

enum class UiThemeMode(val preferenceValue: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemIsDark
    }

    companion object {
        fun fromPreference(value: String?): UiThemeMode = when (value) {
            DARK.preferenceValue -> DARK
            LIGHT.preferenceValue -> LIGHT
            else -> SYSTEM
        }
    }
}

object UiThemePreferences {
    private const val PREFERENCES = "autulio_ui_preferences"
    private const val KEY_THEME_MODE = "theme_mode"

    fun load(context: Context): UiThemeMode {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES,
            Context.MODE_PRIVATE
        )
        return UiThemeMode.fromPreference(preferences.getString(KEY_THEME_MODE, "system"))
    }

    fun save(context: Context, mode: UiThemeMode) {
        context.applicationContext.getSharedPreferences(
            PREFERENCES,
            Context.MODE_PRIVATE
        ).edit().putString(KEY_THEME_MODE, mode.preferenceValue).apply()
    }
}

internal val LocalUiThemeMode = compositionLocalOf { UiThemeMode.SYSTEM }
internal val LocalOnUiThemeModeChange = compositionLocalOf<(UiThemeMode) -> Unit> { {} }
