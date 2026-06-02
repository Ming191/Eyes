plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

fun loadDotEnv(projectDir: java.io.File): Map<String, String> {
    val envFile = projectDir.resolve(".env")
    if (!envFile.exists()) return emptyMap()

    return envFile.readLines()
        .mapNotNull { rawLine ->
            val line = rawLine.trimStart('\uFEFF').trim()
            if (line.isBlank() || line.startsWith("#") || !line.contains("=")) return@mapNotNull null
            val index = line.indexOf("=")
            val key = line.substring(0, index).trim().trimStart('\uFEFF')
            val value = line.substring(index + 1).trim().trim('"')
            if (key.isBlank()) null else key to value
        }
        .toMap()
}

val moduleDotEnv = loadDotEnv(projectDir)
val rootDotEnv = loadDotEnv(rootProject.projectDir)

fun dotEnvValue(key: String): String? = moduleDotEnv[key] ?: rootDotEnv[key]

fun resolveConfigValue(vararg candidates: String?, fallback: String): String {
    return candidates.firstOrNull { !it.isNullOrBlank() } ?: fallback
}

val openAiApiKey = resolveConfigValue(
    providers.gradleProperty("OPENAI_API_KEY").orNull,
    providers.environmentVariable("OPENAI_API_KEY").orNull,
    dotEnvValue("OPENAI_API_KEY"),
    providers.gradleProperty("OPEN_API_KEY").orNull,
    providers.environmentVariable("OPEN_API_KEY").orNull,
    dotEnvValue("OPEN_API_KEY"),
    fallback = ""
).escapeForBuildConfig()
val openAiBaseUrl = resolveConfigValue(
    providers.gradleProperty("OPENAI_BASE_URL").orNull,
    providers.environmentVariable("OPENAI_BASE_URL").orNull,
    dotEnvValue("OPENAI_BASE_URL"),
    fallback = "https://api.openai.com/v1/responses"
).escapeForBuildConfig()
val openAiOcrModel = resolveConfigValue(
    providers.gradleProperty("OPENAI_OCR_MODEL").orNull,
    providers.environmentVariable("OPENAI_OCR_MODEL").orNull,
    dotEnvValue("OPENAI_OCR_MODEL"),
    fallback = "gpt-4o"
).escapeForBuildConfig()

android {
    namespace = "com.example.eyes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.eyes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")
        buildConfigField("String", "OPENAI_BASE_URL", "\"$openAiBaseUrl\"")
        buildConfigField("String", "OPENAI_OCR_MODEL", "\"$openAiOcrModel\"")

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

tasks.withType<Test>().configureEach {
    extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*Activity*.*",
    "**/*Application*.*",
    "**/EyesApp.*",
    "**/ui/**",
    "**/*Screen*.*",
    "**/*Composable*.*",
    "**/*Kt$*.*",
    "**/*\$Companion.*",
    "**/*\$serializer.*",
    "**/*\$DefaultImpls.*",
    "**/*_Factory.*",
    "**/*_MembersInjector.*",
    "**/*_Hilt*.*",
    "**/*Dagger*.*",
    "**/*Koin*.*"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(jacocoExcludes)
    }
    val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
        exclude(jacocoExcludes)
    }

    classDirectories.setFrom(files(kotlinClasses, javaClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include(
            "jacoco/testDebugUnitTest.exec",
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
        )
    })
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")

    val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(jacocoExcludes)
    }
    val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
        exclude(jacocoExcludes)
    }

    classDirectories.setFrom(files(kotlinClasses, javaClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include(
            "jacoco/testDebugUnitTest.exec",
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
        )
    })

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
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
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("ai.djl.huggingface:tokenizers:0.36.0")
    implementation(libs.executorch.android)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
