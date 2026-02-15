package dev.logicojp.reviewer.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/// Resolves executable paths from explicit environment variable values
/// or by scanning the system PATH.
public final class CliPathResolver {

    private CliPathResolver() {
    }

    public static Optional<Path> resolveExplicitExecutable(String envValue, String... allowedNames) {
        if (envValue == null || envValue.isBlank()) {
            return Optional.empty();
        }

        Path explicitPath = Path.of(envValue.trim()).toAbsolutePath().normalize();
        if (!Files.isExecutable(explicitPath)) {
            return Optional.empty();
        }

        String fileName = explicitPath.getFileName().toString();
        boolean validName = Arrays.stream(allowedNames).anyMatch(fileName::equals);
        if (!validName) {
            return Optional.empty();
        }

        try {
            Path realPath = explicitPath.toRealPath();
            String realFileName = realPath.getFileName().toString();
            boolean realNameAllowed = Arrays.stream(allowedNames).anyMatch(realFileName::equals);
            if (!realNameAllowed) {
                return Optional.empty();
            }
            return Optional.of(realPath);
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    public static Optional<Path> findExecutableInPath(String... candidateNames) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }

        for (String entry : pathEnv.split(File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path base = Path.of(entry.trim());
            for (String name : candidateNames) {
                Path candidate = base.resolve(name);
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath().normalize());
                }
            }
        }

        return Optional.empty();
    }
}
