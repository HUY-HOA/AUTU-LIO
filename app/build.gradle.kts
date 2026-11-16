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
    namespace = "com.autulio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.autulio.app"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("AUTU_LIO_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.gradleProperty("AUTU_LIO_VERSION_NAME").orElse("0.1.0").get()
        val authApiBaseUrl = providers.gradleProperty("AUTU_LIO_AUTH_API_BASE_URL")
            .orElse(providers.environmentVariable("AUTU_LIO_AUTH_API_BASE_URL"))
            .getOrElse("")
        val googleServerClientId = providers.gradleProperty("AUTU_LIO_GOOGLE_SERVER_CLIENT_ID")
            .orElse(providers.environmentVariable("AUTU_LIO_GOOGLE_SERVER_CLIENT_ID"))
            .getOrElse("")
        val escapedAuthApiBaseUrl = authApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedGoogleServerClientId = googleServerClientId.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "AUTH_API_BASE_URL", "\"$escapedAuthApiBaseUrl\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$escapedGoogleServerClientId\"")
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
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
