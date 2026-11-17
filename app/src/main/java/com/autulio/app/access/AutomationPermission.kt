package com.autulio.app.access

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.autulio.app.accessibility.AutoClickAccessibilityService

fun hasAutomationAccessibilityPermission(context: Context): Boolean {
    val expected = ComponentName(context, AutoClickAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()

    return enabledServices.split(':').any { value ->
        ComponentName.unflattenFromString(value.trim()) == expected
    }
}

fun openAutomationAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    if (context !is android.app.Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
