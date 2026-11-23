package com.autulio.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

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

@Composable
internal fun PreferredAppTheme(context: Context, content: @Composable () -> Unit) {
    val mode = remember(context.applicationContext) {
        UiThemePreferences.load(context.applicationContext)
    }
    AppTheme(dark = mode.isDark(isSystemInDarkTheme())) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                content()
            }
        }
    }
}
