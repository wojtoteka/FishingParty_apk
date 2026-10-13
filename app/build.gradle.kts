import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Sekret podpisu żądań do serwera rankingu – z local.properties (poza repozytorium).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String, fallback: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name.replace('.', '_').uppercase()) ?: fallback)
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "wojtoteka.ovh.fishingparty"
    compileSdk = 37

    defaultConfig {
        applicationId = "wojtoteka.ovh.fishingparty"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "2.1"

        buildConfigField("String", "FP_API_URL", "\"${localProp("fp.apiUrl", "https://fp.wojtoteka.ovh/api/v1")}\"")
        buildConfigField("String", "FP_HMAC_SECRET", "\"${localProp("fp.hmacSecret")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(26)
        targetCompatibility = JavaVersion.toVersion(26)
    }
    androidResources {
        // Język gry w ustawieniach systemu (Android 13+); domyślnie wg języka telefonu.
        generateLocaleConfig = true
    }
    bundle {
        // Język można zmienić w grze, więc oba muszą być zawsze w paczce z Google Play.
        language { enableSplit = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(27)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_26)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Prawdziwe org.json w testach JVM (w android.jar są tylko atrapy).
    testImplementation(libs.org.json)
}
