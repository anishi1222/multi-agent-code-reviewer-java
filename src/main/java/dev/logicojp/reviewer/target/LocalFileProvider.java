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

    /// Directories to skip during file collection
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
        ".git", ".svn", ".hg",
        "node_modules", "bower_components",
        "target", "build", "out", "dist", "bin", "obj",
        ".gradle", ".mvn", ".idea", ".vscode", ".vs",
        "__pycache__", ".mypy_cache", ".pytest_cache",
        "vendor", ".bundle",
        "coverage", ".nyc_output",
        ".terraform"
    );

    /// Source code file extensions to include
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
        // JVM
        "java", "kt", "kts", "groovy", "scala", "clj",
        // Web
        "js", "jsx", "ts", "tsx", "mjs", "cjs", "vue", "svelte",
        // Systems
        "c", "cpp", "cc", "cxx", "h", "hpp", "rs", "go", "zig",
        // Scripting
        "py", "rb", "php", "pl", "pm", "lua", "r",
        // Shell
        "sh", "bash", "zsh", "fish", "ps1", "psm1",
        // .NET
        "cs", "fs", "vb",
        // Mobile
        "swift", "m", "mm",
        // Data / Config
        "sql", "graphql", "gql", "proto",
        // Markup / Config (commonly reviewed)
        "yaml", "yml", "json", "toml", "xml", "properties",
        // Build
        "gradle", "cmake", "makefile",
        // Docs (small, relevant)
        "md", "rst", "adoc"
    );

    /// Filename patterns indicating potentially sensitive configuration files.
    /// Files matching these patterns are excluded from collection to prevent
    /// accidental transmission of credentials to external LLM services.
    private static final Set<String> SENSITIVE_FILE_PATTERNS = Set.of(
        "application-prod", "application-staging", "application-secret",
        "application-local", "application-dev", "application-ci",
        "secrets", "credentials", ".env",
        ".env.local", ".env.production", ".env.development",
        ".env.staging", ".env.test",
        "service-account", "keystore", "truststore",
        "id_rsa", "id_ed25519", "id_ecdsa",
        ".netrc", ".npmrc", ".pypirc", ".docker/config",
        "vault-config", "aws-credentials",
        "terraform.tfvars", "kubeconfig", ".kube/config",
        "htpasswd", "shadow"
    );

    /// File extensions indicating potentially sensitive files (certificates, keys).
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
        "pem", "key", "p12", "pfx", "jks", "keystore", "cert"
    );

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
        this(baseDirectory,
            LocalFileConfig.DEFAULT_MAX_FILE_SIZE,
            LocalFileConfig.DEFAULT_MAX_TOTAL_SIZE);
    }

    /// Creates a new LocalFileProvider for the given directory with configurable limits.
    /// @param baseDirectory The root directory to collect files from
    /// @param maxFileSize Maximum size per file in bytes
    /// @param maxTotalSize Maximum total content size in bytes
    public LocalFileProvider(Path baseDirectory, long maxFileSize, long maxTotalSize) {
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Base directory must not be null");
        }
        this.maxFileSize = maxFileSize;
        this.maxTotalSize = maxTotalSize;
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
    public List<LocalFile> collectFiles() {
        if (!Files.isDirectory(baseDirectory)) {
            logger.warn("Base directory does not exist or is not a directory: {}", baseDirectory);
            return List.of();
        }

        List<LocalFile> files = new ArrayList<>();
        long totalSize = 0;

        try {
            List<Path> candidates = collectCandidatePaths();

            for (Path path : candidates) {
                try {
                    long size = Files.size(path);
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
                    files.add(new LocalFile(relativePath, content, size));
                    totalSize += size;

                } catch (IOException e) {
                    logger.debug("Failed to read file {}: {}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }

        logger.info("Collected {} source files ({} bytes) from: {}", files.size(), totalSize, baseDirectory);
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
            List<Path> candidates = collectCandidatePaths();
            StringBuilder reviewContentBuilder = new StringBuilder((int) Math.min(maxTotalSize + 4096, Integer.MAX_VALUE));
            StringBuilder fileListBuilder = new StringBuilder();
            long totalSize = 0;
            int fileCount = 0;

            for (Path path : candidates) {
                try {
                    long size = Files.size(path);
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

                    String lang = detectLanguage(relativePath);
                    reviewContentBuilder.append("### ").append(relativePath).append("\n\n");
                    reviewContentBuilder.append("```").append(lang).append("\n");
                    reviewContentBuilder.append(content);
                    if (!content.endsWith("\n")) {
                        reviewContentBuilder.append("\n");
                    }
                    reviewContentBuilder.append("```\n\n");

                    fileListBuilder.append("  - ")
                        .append(relativePath)
                        .append(" (")
                        .append(size)
                        .append(" bytes)\n");

                    totalSize += size;
                    fileCount++;
                } catch (IOException e) {
                    logger.debug("Failed to read file {}: {}", path, e.getMessage());
                }
            }

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

    /// Generates the review content string with all file contents embedded.
    /// Each file is wrapped in a fenced code block with language annotation.
    /// @param files The collected files
    /// @return Formatted review content string
    public String generateReviewContent(List<LocalFile> files) {
        if (files == null || files.isEmpty()) {
            return "(no source files found)";
        }

        long estimatedSize = files.stream().mapToLong(LocalFile::sizeBytes).sum();
        var sb = new StringBuilder((int) Math.min(estimatedSize + files.size() * 30L, maxTotalSize + 4096));
        for (LocalFile file : files) {
            String lang = detectLanguage(file.relativePath());
            sb.append("### ").append(file.relativePath()).append("\n\n");
            sb.append("```").append(lang).append("\n");
            sb.append(file.content());
            if (!file.content().endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```\n\n");
        }
        return sb.toString();
    }

    /// Generates a summary of the directory structure and collected files.
    /// @param files The collected files
    /// @return Directory summary string
    public String generateDirectorySummary(List<LocalFile> files) {
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

    private List<Path> collectCandidatePaths() throws IOException {
        List<Path> candidates = new ArrayList<>();
        Files.walkFileTree(baseDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(baseDirectory)
                    && IGNORED_DIRECTORIES.contains(dir.getFileName().toString())) {
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
                    candidates.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.naturalOrder());
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
        return SOURCE_EXTENSIONS.contains(ext);
    }

    /// Checks if the file's real path is within the base directory.
    /// Prevents symlink-based path traversal attacks.
    /// Uses fast-path normalized check first; falls back to toRealPath() only
    /// when the path is a symlink.
    private boolean isWithinBaseDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attrs) {
        // Fast path: normalized absolute path check (no syscall)
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(baseDirectory)) {
            return false;
        }
        // Skip toRealPath() for non-symlink files — no syscall needed
        if (!attrs.isSymbolicLink()) {
            return true;
        }
        // Slow path: only resolve real path for symlinks
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

        // Check extension-based sensitive files
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = fileName.substring(dotIndex + 1);
            if (SENSITIVE_EXTENSIONS.contains(ext)) {
                return false;
            }
        }

        // Use simple for-loop to avoid Stream API object creation overhead
        // per file in large directory trees
        for (String pattern : SENSITIVE_FILE_PATTERNS) {
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
}
