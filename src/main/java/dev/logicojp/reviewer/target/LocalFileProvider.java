package dev.logicojp.reviewer.target;

import dev.logicojp.reviewer.config.LocalFileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;

/// Collects source files from a local directory for code review.
///
/// Walks the directory tree, filtering for source code files and ignoring
/// common non-source directories (e.g., `.git`, `node_modules`, `target`).
/// Generates a consolidated review content string and a directory summary
/// suitable for inclusion in LLM prompts.
public class LocalFileProvider {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileProvider.class);

    /// Default maximum file size to include (256 KB) — configurable via LocalFileConfig
    private final long maxFileSize;

    /// Default maximum total content size (2 MB) — configurable via LocalFileConfig
    private final long maxTotalSize;

    private final Set<String> ignoredDirectories;
    private final Set<String> sourceExtensions;
    private final Set<String> sensitiveFilePatterns;
    private final Set<String> sensitiveExtensions;

    /// A single collected source file.
    /// @param relativePath Path relative to the base directory
    /// @param content File content as a string
    /// @param sizeBytes Original file size in bytes
    public record LocalFile(String relativePath, String content, long sizeBytes) {}

    /// Combined local-source collection result without retaining per-file content list.
    /// @param reviewContent Formatted review content block
    /// @param directorySummary Directory summary text
    /// @param fileCount Number of collected files
    /// @param totalSizeBytes Total collected size in bytes
    public record CollectionResult(String reviewContent,
                                   String directorySummary,
                                   int fileCount,
                                   long totalSizeBytes) {}

    private final Path baseDirectory;
    private final Path realBaseDirectory;

    /// Creates a new LocalFileProvider for the given directory with default limits.
    /// @param baseDirectory The root directory to collect files from
    public LocalFileProvider(Path baseDirectory) {
        this(baseDirectory, new LocalFileConfig());
    }

    /// Creates a new LocalFileProvider for the given directory with configurable limits.
    /// @param baseDirectory The root directory to collect files from
    /// @param maxFileSize Maximum size per file in bytes
    /// @param maxTotalSize Maximum total content size in bytes
    public LocalFileProvider(Path baseDirectory, long maxFileSize, long maxTotalSize) {
        this(baseDirectory, new LocalFileConfig(maxFileSize, maxTotalSize));
    }

    public LocalFileProvider(Path baseDirectory, LocalFileConfig config) {
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Base directory must not be null");
        }
        this.maxFileSize = config.maxFileSize();
        this.maxTotalSize = config.maxTotalSize();
        this.ignoredDirectories = normalizeSet(config.ignoredDirectories());
        this.sourceExtensions = normalizeSet(config.sourceExtensions());
        this.sensitiveFilePatterns = normalizeSet(config.sensitiveFilePatterns());
        this.sensitiveExtensions = normalizeSet(config.sensitiveExtensions());
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        try {
            this.realBaseDirectory = this.baseDirectory.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve real path for base directory: " + baseDirectory, e);
        }
    }

    /// Collects all source files from the directory tree.
    /// Uses {@link Files#walkFileTree} with {@link FileVisitResult#SKIP_SUBTREE}
    /// to avoid traversing ignored directories (e.g. node_modules, .git, target),
    /// which can contain hundreds of thousands of files.
    /// @return List of collected source files
    List<LocalFile> collectFiles() {
        if (!Files.isDirectory(baseDirectory)) {
            logger.warn("Base directory does not exist or is not a directory: {}", baseDirectory);
            return List.of();
        }

        List<LocalFile> files = new ArrayList<>();

        try {
            List<CandidateFile> candidates = collectCandidateFiles();
            ProcessingResult result = processCandidates(candidates, (relativePath, content, size) ->
                files.add(new LocalFile(relativePath, content, size)));
            logger.info("Collected {} source files ({} bytes) from: {}",
                result.fileCount(), result.totalSize(), baseDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }
        return List.copyOf(files);
    }

    /// Collects local files and generates prompt-ready content in one pass.
    /// Avoids retaining both per-file content list and concatenated content simultaneously.
    public CollectionResult collectAndGenerate() {
        if (!Files.isDirectory(baseDirectory)) {
            logger.warn("Base directory does not exist or is not a directory: {}", baseDirectory);
            return new CollectionResult("(no source files found)",
                "No source files found in: " + baseDirectory, 0, 0);
        }

        try {
            List<CandidateFile> candidates = collectCandidateFiles();
            int reviewCapacity = estimateReviewContentCapacity(candidates);
            StringBuilder reviewContentBuilder = new StringBuilder(reviewCapacity);
            StringBuilder fileListBuilder = new StringBuilder();
            ProcessingResult result = processCandidates(candidates, (relativePath, content, size) -> {
                appendFileBlock(reviewContentBuilder, relativePath, content);

                fileListBuilder.append("  - ")
                    .append(relativePath)
                    .append(" (")
                    .append(size)
                    .append(" bytes)\n");
            });

            long totalSize = result.totalSize();
            int fileCount = result.fileCount();

            String reviewContent = fileCount == 0
                ? "(no source files found)"
                : reviewContentBuilder.toString();

            String directorySummary;
            if (fileCount == 0) {
                directorySummary = "No source files found in: " + baseDirectory;
            } else {
                directorySummary = new StringBuilder()
                    .append("Directory: ").append(baseDirectory).append("\n")
                    .append("Files: ").append(fileCount).append("\n")
                    .append("Total size: ").append(totalSize).append(" bytes\n\n")
                    .append("File list:\n")
                    .append(fileListBuilder)
                    .toString();
            }

            logger.info("Collected {} source files ({} bytes) from: {}", fileCount, totalSize, baseDirectory);
            return new CollectionResult(reviewContent, directorySummary, fileCount, totalSize);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }
    }

    @FunctionalInterface
    private interface FileProcessor {
        void accept(String relativePath, String content, long sizeBytes);
    }

    private record ProcessingResult(long totalSize, int fileCount) {
    }

    private record CandidateFile(Path path, long size) {
    }

    private ProcessingResult processCandidates(List<CandidateFile> candidates, FileProcessor processor) {
        long totalSize = 0;
        int fileCount = 0;

        for (CandidateFile candidate : candidates) {
            try {
                Path path = candidate.path();
                long size = candidate.size();
                if (size > maxFileSize) {
                    logger.debug("Skipping large file ({} bytes): {}", size, path);
                    continue;
                }
                if (totalSize + size > maxTotalSize) {
                    logger.warn("Total content size limit reached ({} bytes). Stopping collection.", totalSize);
                    break;
                }

                String content = Files.readString(path, StandardCharsets.UTF_8);
                String relativePath = baseDirectory.relativize(path).toString().replace('\\', '/');

                processor.accept(relativePath, content, size);
                totalSize += size;
                fileCount++;
            } catch (IOException e) {
                logger.debug("Failed to read file {}: {}", candidate.path(), e.getMessage());
            }
        }

        return new ProcessingResult(totalSize, fileCount);
    }

    /// Generates the review content string with all file contents embedded.
    /// Each file is wrapped in a fenced code block with language annotation.
    /// @param files The collected files
    /// @return Formatted review content string
    String generateReviewContent(List<LocalFile> files) {
        if (files == null || files.isEmpty()) {
            return "(no source files found)";
        }

        long estimatedSize = files.stream().mapToLong(LocalFile::sizeBytes).sum();
        var sb = new StringBuilder((int) Math.min(estimatedSize + files.size() * 30L, maxTotalSize + 4096));
        for (LocalFile file : files) {
            appendFileBlock(sb, file.relativePath(), file.content());
        }
        return sb.toString();
    }

    /// Generates a summary of the directory structure and collected files.
    /// @param files The collected files
    /// @return Directory summary string
    String generateDirectorySummary(List<LocalFile> files) {
        if (files == null || files.isEmpty()) {
            return "No source files found in: " + baseDirectory;
        }

        var sb = new StringBuilder();
        sb.append("Directory: ").append(baseDirectory).append("\n");
        sb.append("Files: ").append(files.size()).append("\n");

        long totalSize = files.stream().mapToLong(LocalFile::sizeBytes).sum();
        sb.append("Total size: ").append(totalSize).append(" bytes\n");
        sb.append("\nFile list:\n");

        for (LocalFile file : files) {
            sb.append("  - ").append(file.relativePath())
                .append(" (").append(file.sizeBytes()).append(" bytes)\n");
        }

        return sb.toString();
    }

    private int estimateReviewContentCapacity(List<CandidateFile> candidates) {
        long estimatedSize = 0;
        for (CandidateFile candidate : candidates) {
            estimatedSize += candidate.size() + 64L;
            if (estimatedSize >= maxTotalSize) {
                break;
            }
        }
        return (int) Math.min(estimatedSize + 1024L, maxTotalSize + 4096);
    }

    private void appendFileBlock(StringBuilder sb, String relativePath, String content) {
        String lang = detectLanguage(relativePath);
        sb.append("### ").append(relativePath).append("\n\n");
        sb.append("```").append(lang).append("\n");
        sb.append(content);
        if (!content.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("```\n\n");
    }

    private List<CandidateFile> collectCandidateFiles() throws IOException {
        List<CandidateFile> candidates = new ArrayList<>();
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
                if (attrs.isRegularFile() && !attrs.isSymbolicLink()
                    && isWithinBaseDirectory(file, attrs)
                    && isSourceFile(file)
                    && isNotSensitiveFile(file)) {
                    candidates.add(new CandidateFile(file, attrs.size()));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.comparing(CandidateFile::path));
        return candidates;
    }

    private boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        // Include specific filenames without extensions
        if (fileName.equals("makefile") || fileName.equals("dockerfile")
            || fileName.equals("rakefile") || fileName.equals("gemfile")) {
            return true;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String ext = fileName.substring(dotIndex + 1);
        return sourceExtensions.contains(ext);
    }

    /// Checks if the file's real path is within the base directory.
    /// Prevents symlink-based path traversal attacks.
    /// Uses fast-path normalized check first; falls back to toRealPath() only
    /// when the path is a symlink.
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

    /// Checks if the file matches a sensitive configuration file pattern.
    /// Excludes files that may contain credentials or secrets.
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

    private String detectLanguage(String relativePath) {
        int dotIndex = relativePath.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        String ext = relativePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "js", "mjs", "cjs" -> "javascript";
            case "ts" -> "typescript";
            case "jsx" -> "jsx";
            case "tsx" -> "tsx";
            case "py" -> "python";
            case "rb" -> "ruby";
            case "rs" -> "rust";
            case "kt", "kts" -> "kotlin";
            case "cs" -> "csharp";
            case "fs" -> "fsharp";
            case "sh", "bash", "zsh" -> "bash";
            case "ps1", "psm1" -> "powershell";
            case "yml" -> "yaml";
            case "md" -> "markdown";
            default -> ext;
        };
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }
}
