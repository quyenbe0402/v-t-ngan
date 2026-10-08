plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

android {
    namespace = "com.vitngan.harness"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vitngan.harness"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Key từ local.properties (KHÔNG tự load — đọc tay) hoặc Env; không hardcode/commit.
        // local.properties đã gitignore (root .gitignore) — đúng API_CONFIG.md.
        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { stream -> localProps.load(stream) }
        }
        val explabsKey: String = (
            localProps.getProperty("EXPLABS_API_KEY")
                ?: providers.environmentVariable("EXPLABS_API_KEY").getOrElse("")
            ).trim()
        buildConfigField("String", "EXPLABS_API_KEY", "\"$explabsKey\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }
}

dependencies {
    val shizuku_version = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizuku_version")
    implementation("dev.rikka.shizuku:provider:$shizuku_version")
    // E4-1 Dadb fallback (Apache-2.0, verified API từ source mobile-dev-inc/dadb)
    implementation("dev.mobile:dadb:2.0.0")
    // E5-2 Dashboard MVP (MIT/BSD, D-006)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.annotation:annotation:1.9.1")
    // Chỉ để thỏa mãn Compose compiler check (probe dùng View thường, không dùng Compose UI)
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.runtime:runtime")
    // E6-1 UI Compose cho probe (khắc phục render trắng của View thường trên máy test)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
