package dev.logicojp.reviewer.report.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/// Shared file helpers for report generation.
public final class ReportFileUtils {

    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS =
        PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
        PosixFilePermissions.fromString("rw-------");

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
        if (supportsPosix(outputDirectory)) {
            Files.createDirectories(outputDirectory,
                PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY_PERMISSIONS));
            Files.setPosixFilePermissions(outputDirectory, OWNER_DIRECTORY_PERMISSIONS);
            return;
        }
        Files.createDirectories(outputDirectory);
    }

    public static void writeSecureString(Path filePath, String content) throws IOException {
        Files.writeString(filePath, content);
        if (supportsPosix(filePath)) {
            Files.setPosixFilePermissions(filePath, OWNER_FILE_PERMISSIONS);
        }
    }

    private static boolean supportsPosix(Path path) {
        Path target = path;
        if (!Files.exists(target)) {
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                target = parent;
            }
        }
        return Files.getFileAttributeView(target, PosixFileAttributeView.class) != null;
    }
}
