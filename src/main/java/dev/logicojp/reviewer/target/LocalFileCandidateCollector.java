package dev.logicojp.reviewer.target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Collects candidate source files from a local directory tree.
final class LocalFileCandidateCollector {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileCandidateCollector.class);

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final Set<String> ignoredDirectories;
    private final Set<String> sourceExtensions;
    private final Set<String> sensitiveFilePatterns;
    private final Set<String> sensitiveExtensions;

    LocalFileCandidateCollector(Path baseDirectory,
                                Path realBaseDirectory,
                                Set<String> ignoredDirectories,
                                Set<String> sourceExtensions,
                                Set<String> sensitiveFilePatterns,
                                Set<String> sensitiveExtensions) {
        this.baseDirectory = baseDirectory;
        this.realBaseDirectory = realBaseDirectory;
        this.ignoredDirectories = ignoredDirectories;
        this.sourceExtensions = sourceExtensions;
        this.sensitiveFilePatterns = sensitiveFilePatterns;
        this.sensitiveExtensions = sensitiveExtensions;
    }

    List<LocalFileCandidate> collectCandidateFiles() throws IOException {
        List<LocalFileCandidate> candidates = new ArrayList<>();
        Files.walkFileTree(baseDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(baseDirectory)
                    && ignoredDirectories.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isCollectableCandidate(file, attrs)) {
                    candidates.add(toCandidate(file, attrs));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.comparing(LocalFileCandidate::path));
        return candidates;
    }

    private boolean isCollectableCandidate(Path file, BasicFileAttributes attrs) {
        return attrs.isRegularFile()
            && !attrs.isSymbolicLink()
            && isWithinBaseDirectory(file, attrs)
            && isSourceFile(file)
            && isNotSensitiveFile(file);
    }

    private LocalFileCandidate toCandidate(Path file, BasicFileAttributes attrs) {
        return new LocalFileCandidate(file, attrs.size());
    }

    private boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (isSpecialSourceFilename(fileName)) {
            return true;
        }

        return hasSourceExtension(fileName);
    }

    private boolean isSpecialSourceFilename(String fileName) {
        return fileName.equals("makefile")
            || fileName.equals("dockerfile")
            || fileName.equals("rakefile")
            || fileName.equals("gemfile");
    }

    private boolean hasSourceExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = fileName.substring(dotIndex + 1);
        return sourceExtensions.contains(ext);
    }

    private boolean isWithinBaseDirectory(Path path, BasicFileAttributes attrs) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(baseDirectory)) {
            return false;
        }
        if (!attrs.isSymbolicLink()) {
            return true;
        }
        try {
            Path realPath = path.toRealPath();
            return realPath.startsWith(realBaseDirectory);
        } catch (IOException e) {
            logger.debug("Cannot resolve real path for {}: {}", path, e.getMessage());
            return false;
        }
    }

    private boolean isNotSensitiveFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = fileName.substring(dotIndex + 1);
            if (sensitiveExtensions.contains(ext)) {
                return false;
            }
        }

        for (String pattern : sensitiveFilePatterns) {
            if (fileName.contains(pattern)) {
                return false;
            }
        }
        return true;
    }
}
