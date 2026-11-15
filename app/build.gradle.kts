plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.autulio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.autulio.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
