plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val autuApiBaseUrl = providers.gradleProperty("AUTULIO_API_BASE_URL").orElse("")
val autuGoogleWebClientId = providers.gradleProperty("AUTULIO_GOOGLE_WEB_CLIENT_ID").orElse("")

android {
    namespace = "com.autulio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.autulio.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "AUTULIO_API_BASE_URL", "\"${autuApiBaseUrl.get()}\"")
        buildConfigField("String", "AUTULIO_GOOGLE_WEB_CLIENT_ID", "\"${autuGoogleWebClientId.get()}\"")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.credential.manager)
    implementation(libs.credential.manager.play.services.auth)
    implementation(libs.googleid)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
