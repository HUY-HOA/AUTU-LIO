package com.autu.lio

import com.autu.lio.BuildConfig
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object FirebasePushManager {
    const val EXTRA_OPEN_UPDATE_CHECK = "com.autu.lio.OPEN_UPDATE_CHECK"
    private const val TOPIC = "autu-lio-update-notices"
    private const val PREFS = "firebase_push_settings"
    private const val KEY_PERMISSION_REQUESTED = "notification_permission_requested"
    private const val PERMISSION_REQUEST_CODE = 7842

    fun isUpdateIntent(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_UPDATE_CHECK, false) == true

    fun initialize(activity: Activity) {
        val context = activity.applicationContext
        val apiKey = BuildConfig.FIREBASE_API_KEY.trim()
        val projectId = BuildConfig.FIREBASE_PROJECT_ID.trim()
        val senderId = BuildConfig.FIREBASE_SENDER_ID.trim()
        val appId = BuildConfig.FIREBASE_APP_ID.trim()
        if (apiKey.isBlank() || projectId.isBlank() || senderId.isBlank() || appId.isBlank()) {
            Log.i("AUTU-LIO-FCM", "Firebase app configuration is not supplied; push registration is disabled.")
            return
        }

        try {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setGcmSenderId(senderId)
                .setApplicationId(appId)
                .build()
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context, options) ?: return
            }
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
                .addOnSuccessListener { Log.i("AUTU-LIO-FCM", "Subscribed to update notices.") }
                .addOnFailureListener { error -> Log.w("AUTU-LIO-FCM", "Could not subscribe to update notices.", error) }
        } catch (error: Exception) {
            Log.e("AUTU-LIO-FCM", "Firebase Messaging initialization failed.", error)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = context.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
                prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
            }
        }
    }
}
