package dev.logicojp.reviewer.report.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Shared file helpers for report generation.
public final class ReportFileUtils {

    private ReportFileUtils() {
    }

    public static void ensureOutputDirectory(Path outputDirectory) throws IOException {
        if (isMissing(outputDirectory)) {
            createDirectories(outputDirectory);
        }
    }

    private static boolean isMissing(Path outputDirectory) {
        return !Files.exists(outputDirectory);
    }

    private static void createDirectories(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
    }
}
