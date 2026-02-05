package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FileExtensionUtils.
 */
class FileExtensionUtilsTest {

    // === getExtension Tests ===

    @ParameterizedTest
    @CsvSource({
        "Test.java, java",
        "script.py, py",
        "config.yaml, yaml",
        "config.yml, yml",
        "app.tsx, tsx",
        "FILE.JAVA, java",
        "my.file.name.txt, txt"
    })
    void getExtension_shouldReturnCorrectExtension(String filename, String expected) {
        assertThat(FileExtensionUtils.getExtension(filename)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getExtension_shouldReturnEmptyForNullOrEmpty(String filename) {
        assertThat(FileExtensionUtils.getExtension(filename)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"noextension", "Makefile", "Dockerfile", ".gitignore", ".env"})
    void getExtension_shouldReturnEmptyForFilesWithoutExtension(String filename) {
        assertThat(FileExtensionUtils.getExtension(filename)).isEmpty();
    }

    @Test
    void getExtension_shouldReturnEmptyForDotAtEnd() {
        assertThat(FileExtensionUtils.getExtension("file.")).isEmpty();
    }

    // === getLanguageForFile Tests ===

    @ParameterizedTest
    @CsvSource({
        "Test.java, java",
        "script.py, python",
        "app.js, javascript",
        "component.jsx, javascript",
        "service.ts, typescript",
        "component.tsx, typescript",
        "main.go, go",
        "lib.rs, rust",
        "program.c, c",
        "header.h, c",
        "impl.cpp, cpp",
        "header.hpp, cpp",
        "Program.cs, csharp",
        "config.yaml, yaml",
        "config.yml, yaml",
        "data.json, json",
        "document.xml, xml",
        "README.md, markdown",
        "script.sh, bash",
        "script.bash, bash",
        "Dockerfile.dockerfile, dockerfile",
        "main.tf, hcl"
    })
    void getLanguageForFile_shouldReturnCorrectLanguage(String filename, String expected) {
        assertThat(FileExtensionUtils.getLanguageForFile(filename)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getLanguageForFile_shouldReturnEmptyForNullOrEmpty(String filename) {
        assertThat(FileExtensionUtils.getLanguageForFile(filename)).isEmpty();
    }

    @Test
    void getLanguageForFile_shouldReturnEmptyForUnknownExtension() {
        assertThat(FileExtensionUtils.getLanguageForFile("file.unknown")).isEmpty();
    }

    @Test
    void getLanguageForFile_shouldHandlePathsWithDirectories() {
        assertThat(FileExtensionUtils.getLanguageForFile("src/main/java/Test.java"))
            .isEqualTo("java");
    }

    // === isKnownExtension Tests ===

    @ParameterizedTest
    @ValueSource(strings = {"java", "py", "js", "ts", "go", "rs", "yaml", "json"})
    void isKnownExtension_shouldReturnTrueForKnownExtensions(String extension) {
        assertThat(FileExtensionUtils.isKnownExtension(extension)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "xyz", "abc"})
    void isKnownExtension_shouldReturnFalseForUnknownExtensions(String extension) {
        assertThat(FileExtensionUtils.isKnownExtension(extension)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isKnownExtension_shouldReturnFalseForNullOrEmpty(String extension) {
        assertThat(FileExtensionUtils.isKnownExtension(extension)).isFalse();
    }

    @Test
    void isKnownExtension_shouldBeCaseInsensitive() {
        assertThat(FileExtensionUtils.isKnownExtension("JAVA")).isTrue();
        assertThat(FileExtensionUtils.isKnownExtension("Java")).isTrue();
    }
}
