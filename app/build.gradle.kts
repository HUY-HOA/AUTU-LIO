plugins {
    alias(libs.plugins.android.application)
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
    }

    flavorDimensions += "edition"

    productFlavors {
        create("admin") {
            dimension = "edition"
            applicationIdSuffix = ".admin"
            resValue("string", "app_name", "AUTU-LIO Admin Studio")
            buildConfigField("String", "EDITION_NAME", "\"Admin Studio\"")
        }
        create("client") {
            dimension = "edition"
            applicationIdSuffix = ".client"
            resValue("string", "app_name", "AUTU-LIO AutoClick")
            buildConfigField("String", "EDITION_NAME", "\"AutoClick\"")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
