package dev.logicojp.reviewer.target;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class LocalFileContentFormatter {

    private final Path baseDirectory;
    private final long maxTotalSize;

    LocalFileContentFormatter(Path baseDirectory, long maxTotalSize) {
        this.baseDirectory = baseDirectory;
        this.maxTotalSize = maxTotalSize;
    }

    int estimateReviewContentCapacity(List<LocalFileCandidate> candidates) {
        long estimatedSize = 0;
        for (LocalFileCandidate candidate : candidates) {
            estimatedSize += candidate.size() + 64L;
            if (estimatedSize >= maxTotalSize) {
                break;
            }
        }
        return (int) Math.min(estimatedSize + 1024L, maxTotalSize + 4096);
    }

    void appendFileBlock(StringBuilder sb, String relativePath, String content) {
        String lang = detectLanguage(relativePath);
        sb.append("### ").append(relativePath).append("\n\n");
        sb.append("```").append(lang).append("\n");
        sb.append(content);
        if (!content.endsWith("\n")) {
            sb.append("\n");
        }
        sb.append("```\n\n");
    }

    String generateReviewContent(List<LocalFileProvider.LocalFile> files) {
        if (files == null || files.isEmpty()) {
            return "(no source files found)";
        }

        long estimatedSize = files.stream().mapToLong(LocalFileProvider.LocalFile::sizeBytes).sum();
        var sb = new StringBuilder((int) Math.min(estimatedSize + files.size() * 30L, maxTotalSize + 4096));
        for (LocalFileProvider.LocalFile file : files) {
            appendFileBlock(sb, file.relativePath(), file.content());
        }
        return sb.toString();
    }

    String generateDirectorySummary(List<LocalFileProvider.LocalFile> files) {
        if (files == null || files.isEmpty()) {
            return noSourceFilesSummary();
        }
        long totalSize = files.stream().mapToLong(LocalFileProvider.LocalFile::sizeBytes).sum();
        StringBuilder fileListBuilder = buildFileList(files);

        return generateDirectorySummary(files.size(), totalSize, fileListBuilder);
    }

    private StringBuilder buildFileList(List<LocalFileProvider.LocalFile> files) {
        var fileListBuilder = new StringBuilder();
        for (LocalFileProvider.LocalFile file : files) {
            fileListBuilder.append("  - ").append(file.relativePath())
                .append(" (").append(file.sizeBytes()).append(" bytes)\n");
        }
        return fileListBuilder;
    }

    String generateDirectorySummary(int fileCount, long totalSize, StringBuilder fileListBuilder) {
        if (fileCount == 0) {
            return noSourceFilesSummary();
        }
        return new StringBuilder()
            .append("Directory: ").append(baseDirectory).append("\n")
            .append("Files: ").append(fileCount).append("\n")
            .append("Total size: ").append(totalSize).append(" bytes\n\n")
            .append("File list:\n")
            .append(fileListBuilder)
            .toString();
    }

    String noSourceFilesSummary() {
        return "No source files found in: " + baseDirectory;
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
