package dev.logicojp.reviewer.target;

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
import java.util.*;

/// Collects source files from a local directory for code review.
///
/// Walks the directory tree, filtering for source code files and ignoring
/// common non-source directories (e.g., `.git`, `node_modules`, `target`).
/// Generates a consolidated review content string and a directory summary
/// suitable for inclusion in LLM prompts.
public class LocalFileProvider {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileProvider.class);

    /// Maximum file size to include (256 KB)
    private static final long MAX_FILE_SIZE = 256 * 1024;

    /// Maximum total content size (2 MB)
    private static final long MAX_TOTAL_SIZE = 2 * 1024 * 1024;

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
        "secrets", "credentials", ".env",
        "service-account", "keystore", "truststore",
        "id_rsa", "id_ed25519", "id_ecdsa"
    );

    /// A single collected source file.
    /// @param relativePath Path relative to the base directory
    /// @param content File content as a string
    /// @param sizeBytes Original file size in bytes
    public record LocalFile(String relativePath, String content, long sizeBytes) {}

    private final Path baseDirectory;

    /// Creates a new LocalFileProvider for the given directory.
    /// @param baseDirectory The root directory to collect files from
    public LocalFileProvider(Path baseDirectory) {
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Base directory must not be null");
        }
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
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
        long[] totalSize = {0};

        try {
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
                            && isWithinBaseDirectory(file)
                            && isSourceFile(file)
                            && isNotSensitiveFile(file)) {
                        candidates.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            candidates.sort(Comparator.naturalOrder());

            for (Path path : candidates) {
                try {
                    long size = Files.size(path);
                    if (size > MAX_FILE_SIZE) {
                        logger.debug("Skipping large file ({} bytes): {}", size, path);
                        continue;
                    }
                    if (totalSize[0] + size > MAX_TOTAL_SIZE) {
                        logger.warn("Total content size limit reached ({} bytes). Stopping collection.", totalSize[0]);
                        break;
                    }

                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    String relativePath = baseDirectory.relativize(path).toString().replace('\\', '/');
                    files.add(new LocalFile(relativePath, content, size));
                    totalSize[0] += size;

                } catch (IOException e) {
                    logger.debug("Failed to read file {}: {}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }

        logger.info("Collected {} source files ({} bytes) from: {}", files.size(), totalSize[0], baseDirectory);
        return List.copyOf(files);
    }

    /// Generates the review content string with all file contents embedded.
    /// Each file is wrapped in a fenced code block with language annotation.
    /// @param files The collected files
    /// @return Formatted review content string
    public String generateReviewContent(List<LocalFile> files) {
        if (files == null || files.isEmpty()) {
            return "(no source files found)";
        }

        var sb = new StringBuilder();
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
    private boolean isWithinBaseDirectory(Path path) {
        try {
            Path realPath = path.toRealPath();
            Path realBase = baseDirectory.toRealPath();
            return realPath.startsWith(realBase);
        } catch (IOException e) {
            logger.debug("Cannot resolve real path for {}: {}", path, e.getMessage());
            return false;
        }
    }

    /// Checks if the file matches a sensitive configuration file pattern.
    /// Excludes files that may contain credentials or secrets.
    private boolean isNotSensitiveFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SENSITIVE_FILE_PATTERNS.stream()
            .noneMatch(fileName::contains);
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
