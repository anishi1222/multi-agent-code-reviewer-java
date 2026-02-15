package dev.logicojp.reviewer.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Shared file helpers for report generation.
public final class ReportFileUtils {

    private ReportFileUtils() {
    }

    public static void ensureOutputDirectory(Path outputDirectory) throws IOException {
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
        }
    }
}
