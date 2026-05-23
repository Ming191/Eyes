plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.screenshot)
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

fun loadDotEnv(projectDir: java.io.File): Map<String, String> {
    val envFile = projectDir.resolve(".env")
    if (!envFile.exists()) return emptyMap()

    return envFile.readLines()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#") || !line.contains("=")) return@mapNotNull null
            val index = line.indexOf("=")
            val key = line.substring(0, index).trim()
            val value = line.substring(index + 1).trim().trim('"')
            if (key.isBlank()) null else key to value
        }
        .toMap()
}

val dotEnv = loadDotEnv(projectDir)

fun resolveConfigValue(vararg candidates: String?, fallback: String): String {
    return candidates.firstOrNull { !it.isNullOrBlank() } ?: fallback
}

val openAiApiKey = resolveConfigValue(
    providers.gradleProperty("OPENAI_API_KEY").orNull,
    providers.environmentVariable("OPENAI_API_KEY").orNull,
    dotEnv["OPENAI_API_KEY"],
    providers.gradleProperty("OPEN_API_KEY").orNull,
    providers.environmentVariable("OPEN_API_KEY").orNull,
    dotEnv["OPEN_API_KEY"],
    fallback = ""
).escapeForBuildConfig()
val openAiBaseUrl = resolveConfigValue(
    providers.gradleProperty("OPENAI_BASE_URL").orNull,
    providers.environmentVariable("OPENAI_BASE_URL").orNull,
    dotEnv["OPENAI_BASE_URL"],
    fallback = "https://api.openai.com/v1/responses"
).escapeForBuildConfig()
val openAiOcrModel = resolveConfigValue(
    providers.gradleProperty("OPENAI_OCR_MODEL").orNull,
    providers.environmentVariable("OPENAI_OCR_MODEL").orNull,
    dotEnv["OPENAI_OCR_MODEL"],
    fallback = "gpt-4o"
).escapeForBuildConfig()

android {
    namespace = "com.example.eyes"
    compileSdk = 36
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    val rawBackendUrl = (project.findProperty("BACKEND_URL") as? String)
        ?.takeIf { it.isNotBlank() }
        ?: "https://example.com/"
    val backendUrl = if (rawBackendUrl.endsWith("/")) rawBackendUrl else "$rawBackendUrl/"

    defaultConfig {
        applicationId = "com.example.eyes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")
        buildConfigField("String", "OPENAI_BASE_URL", "\"$openAiBaseUrl\"")
        buildConfigField("String", "OPENAI_OCR_MODEL", "\"$openAiOcrModel\"")
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}
