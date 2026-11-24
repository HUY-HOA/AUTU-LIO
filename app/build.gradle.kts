plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystorePath = providers.gradleProperty("AUTU_LIO_RELEASE_KEYSTORE_PATH")
    .orElse(providers.environmentVariable("AUTU_LIO_RELEASE_KEYSTORE_PATH"))
    .orNull
val releaseStorePassword = providers.gradleProperty("AUTU_LIO_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("AUTU_LIO_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("AUTU_LIO_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("AUTU_LIO_RELEASE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("AUTU_LIO_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("AUTU_LIO_RELEASE_KEY_PASSWORD"))
    .orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.autu.lio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.autu.lio"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("AUTU_LIO_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.gradleProperty("AUTU_LIO_VERSION_NAME").orElse("0.1.0").get()
        val firebaseApiKey = providers.environmentVariable("FIREBASE_API_KEY").orElse("").get()
        val firebaseProjectId = providers.environmentVariable("FIREBASE_PROJECT_ID").orElse("").get()
        val firebaseSenderId = providers.environmentVariable("FIREBASE_SENDER_ID").orElse("").get()
        val firebaseAppId = providers.environmentVariable("FIREBASE_APP_ID").orElse("").get()
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            optimization {
                enable = true
            }
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(libs.androidx.activity.compose)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkitObjectDetection)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
