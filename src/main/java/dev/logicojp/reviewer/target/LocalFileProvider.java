package dev.logicojp.reviewer.target;

import dev.logicojp.reviewer.config.LocalFileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private final LocalFileCandidateCollector candidateCollector;
    private final LocalFileCandidateProcessor candidateProcessor;
    private final LocalFileContentFormatter contentFormatter;

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
        LocalFileSelectionConfig selectionConfig = LocalFileSelectionConfig.from(config);
        this.maxFileSize = selectionConfig.maxFileSize();
        this.maxTotalSize = selectionConfig.maxTotalSize();
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        try {
            this.realBaseDirectory = this.baseDirectory.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve real path for base directory: " + baseDirectory, e);
        }
        this.candidateCollector = new LocalFileCandidateCollector(
            this.baseDirectory,
            this.realBaseDirectory,
            selectionConfig.ignoredDirectories(),
            selectionConfig.sourceExtensions(),
            selectionConfig.sensitiveFilePatterns(),
            selectionConfig.sensitiveExtensions()
        );
        this.candidateProcessor = new LocalFileCandidateProcessor(this.baseDirectory, this.maxFileSize, this.maxTotalSize);
        this.contentFormatter = new LocalFileContentFormatter(this.baseDirectory, this.maxTotalSize);
    }

    /// Collects all source files from the directory tree.
    /// Uses {@link Files#walkFileTree} with {@link FileVisitResult#SKIP_SUBTREE}
    /// to avoid traversing ignored directories (e.g. node_modules, .git, target),
    /// which can contain hundreds of thousands of files.
    /// @return List of collected source files
    List<LocalFile> collectFiles() {
        if (isMissingBaseDirectory()) {
            logMissingBaseDirectory();
            return List.of();
        }

        List<LocalFile> files = new ArrayList<>();

        try {
            List<LocalFileCandidate> candidates = candidateCollector.collectCandidateFiles();
            LocalFileCandidateProcessor.ProcessingResult result = candidateProcessor.process(candidates, (relativePath, content, size) ->
                files.add(new LocalFile(relativePath, content, size)));
            logCollectedFiles(result.fileCount(), result.totalSize());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }
        return List.copyOf(files);
    }

    /// Collects local files and generates prompt-ready content in one pass.
    /// Avoids retaining both per-file content list and concatenated content simultaneously.
    public CollectionResult collectAndGenerate() {
        if (isMissingBaseDirectory()) {
            logMissingBaseDirectory();
            return new CollectionResult("(no source files found)",
                contentFormatter.noSourceFilesSummary(), 0, 0);
        }

        try {
            List<LocalFileCandidate> candidates = candidateCollector.collectCandidateFiles();
            int reviewCapacity = contentFormatter.estimateReviewContentCapacity(candidates);
            StringBuilder reviewContentBuilder = new StringBuilder(reviewCapacity);
            StringBuilder fileListBuilder = new StringBuilder();
            LocalFileCandidateProcessor.ProcessingResult result = candidateProcessor.process(candidates, (relativePath, content, size) -> {
                contentFormatter.appendFileBlock(reviewContentBuilder, relativePath, content);

                fileListBuilder.append("  - ")
                    .append(relativePath)
                    .append(" (")
                    .append(size)
                    .append(" bytes)\n");
            });

            long totalSize = result.totalSize();
            int fileCount = result.fileCount();
            logCollectedFiles(fileCount, totalSize);
            return createCollectionResult(fileCount, totalSize, reviewContentBuilder, fileListBuilder);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + baseDirectory, e);
        }
    }

    private boolean isMissingBaseDirectory() {
        return !Files.isDirectory(baseDirectory);
    }

    private void logMissingBaseDirectory() {
        logger.warn("Base directory does not exist or is not a directory: {}", baseDirectory);
    }

    private void logCollectedFiles(int fileCount, long totalSize) {
        logger.info("Collected {} source files ({} bytes) from: {}", fileCount, totalSize, baseDirectory);
    }

    private CollectionResult createCollectionResult(int fileCount,
                                                    long totalSize,
                                                    StringBuilder reviewContentBuilder,
                                                    StringBuilder fileListBuilder) {
        String reviewContent = fileCount == 0
            ? "(no source files found)"
            : reviewContentBuilder.toString();
        String directorySummary = contentFormatter.generateDirectorySummary(fileCount, totalSize, fileListBuilder);
        return new CollectionResult(reviewContent, directorySummary, fileCount, totalSize);
    }

    /// Generates the review content string with all file contents embedded.
    /// Each file is wrapped in a fenced code block with language annotation.
    /// @param files The collected files
    /// @return Formatted review content string
    String generateReviewContent(List<LocalFile> files) {
        return contentFormatter.generateReviewContent(files);
    }

    /// Generates a summary of the directory structure and collected files.
    /// @param files The collected files
    /// @return Directory summary string
    String generateDirectorySummary(List<LocalFile> files) {
        return contentFormatter.generateDirectorySummary(files);
    }

}
