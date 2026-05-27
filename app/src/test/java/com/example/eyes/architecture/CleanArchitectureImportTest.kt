package com.example.eyes.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CleanArchitectureImportTest {

    private val projectRoot: File = findProjectRoot()
    private val sourceRoot = File(projectRoot, "app/src/main/java")

    @Test
    fun domain_doesNotImportOuterLayersOrImplementations() {
        assertNoForbiddenImports(
            sourceSet = "com/example/eyes/domain",
            forbiddenImports = listOf(
                "com.example.eyes.data",
                "com.example.eyes.ui",
                "com.example.eyes.infrastructure.system",
                "com.example.eyes.ocr",
                "com.example.eyes.objectdetection",
                "com.example.eyes.infrastructure.camera",
                "com.example.eyes.di",
            ),
        )
    }

    @Test
    fun data_doesNotImportUi() {
        assertNoForbiddenImports(
            sourceSet = "com/example/eyes/data",
            forbiddenImports = listOf("com.example.eyes.ui"),
        )
    }

    @Test
    fun application_doesNotImportDataUiOrDi() {
        assertNoForbiddenImports(
            sourceSet = "com/example/eyes/application",
            forbiddenImports = listOf(
                "com.example.eyes.data",
                "com.example.eyes.ui",
                "com.example.eyes.di",
            ),
        )
    }

    @Test
    fun ui_doesNotImportRemoteDataImplementations() {
        assertNoForbiddenImports(
            sourceSet = "com/example/eyes/ui",
            forbiddenImports = listOf("com.example.eyes.data.remote"),
        )
    }

    @Test
    fun oldInfrastructurePorts_areNotImported() {
        assertNoForbiddenImports(
            sourceSet = "com/example/eyes",
            forbiddenImports = listOf(
                "com.example.eyes.ocr.OcrEngine",
                "com.example.eyes.ocr.OcrTranslator",
                "com.example.eyes.objectdetection.ObjectDetector",
            ),
        )
    }

    private fun assertNoForbiddenImports(sourceSet: String, forbiddenImports: List<String>) {
        val sourceDirectory = File(sourceRoot, sourceSet)
        val violations = sourceDirectory
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> forbiddenImportViolations(file, forbiddenImports).asSequence() }
            .toList()

        assertTrue(
            "Forbidden imports found:\n${violations.joinToString(separator = "\n")}",
            violations.isEmpty(),
        )
    }

    private fun forbiddenImportViolations(file: File, forbiddenImports: List<String>): List<String> {
        val relativePath = file.relativeTo(projectRoot).invariantSeparatorsPath
        return importRegex.findAll(file.readText())
            .map { match -> match.groupValues[1] }
            .filter { importedPackage ->
                forbiddenImports.any { forbiddenImport ->
                    importedPackage == forbiddenImport || importedPackage.startsWith("$forbiddenImport.")
                }
            }
            .map { forbiddenImport -> "$relativePath imports $forbiddenImport" }
            .toList()
    }

    private fun findProjectRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir system property is missing" }
        return generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .firstOrNull { candidate -> File(candidate, "app/src/main/java").isDirectory }
            ?: error("Could not locate project root from user.dir=$userDir")
    }

    private companion object {
        val importRegex = Regex("(?m)^\\s*import\\s+([^\\s]+)")
    }
}
