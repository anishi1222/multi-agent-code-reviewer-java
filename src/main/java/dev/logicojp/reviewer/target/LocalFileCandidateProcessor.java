package dev.logicojp.reviewer.target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class LocalFileCandidateProcessor {

    @FunctionalInterface
    interface FileConsumer {
        void accept(String relativePath, String content, long sizeBytes);
    }

    record ProcessingResult(long totalSize, int fileCount) {
    }

    private static final Logger logger = LoggerFactory.getLogger(LocalFileCandidateProcessor.class);

    private final Path baseDirectory;
    private final Path realBaseDirectory;
    private final long maxFileSize;
    private final long maxTotalSize;

    LocalFileCandidateProcessor(Path baseDirectory,
                                Path realBaseDirectory,
                                long maxFileSize,
                                long maxTotalSize) {
        this.baseDirectory = baseDirectory;
        this.realBaseDirectory = realBaseDirectory;
        this.maxFileSize = maxFileSize;
        this.maxTotalSize = maxTotalSize;
    }

    ProcessingResult process(List<LocalFileCandidate> candidates, FileConsumer consumer) {
        long totalSize = 0;
        int fileCount = 0;

        for (LocalFileCandidate candidate : candidates) {
            ProcessedCandidate processed = processCandidate(candidate, totalSize);
            if (processed.stopProcessing()) {
                break;
            }
            if (!processed.included()) {
                continue;
            }
            consumer.accept(processed.relativePath(), processed.content(), processed.size());
            totalSize += processed.size();
            fileCount++;
        }

        return new ProcessingResult(totalSize, fileCount);
    }

    private ProcessedCandidate processCandidate(LocalFileCandidate candidate, long totalSize) {
        Path path = candidate.path();
        long size = candidate.size();
        if (isTooLarge(size)) {
            logSkippedLargeFile(path, size);
            return ProcessedCandidate.skip();
        }
        if (wouldExceedTotalSize(totalSize, size)) {
            logTotalSizeLimitReached(totalSize);
            return ProcessedCandidate.stop();
        }

        try {
            if (Files.isSymbolicLink(path)) {
                logger.warn("File became symbolic link after collection, skipping: {}", path);
                return ProcessedCandidate.skip();
            }

            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realBaseDirectory)) {
                logger.warn("File escaped base directory (possible race), skipping: {}", path);
                return ProcessedCandidate.skip();
            }

            String content = Files.readString(realPath, StandardCharsets.UTF_8);
            String relativePath = toRelativePath(path);
            return ProcessedCandidate.included(relativePath, content, size);
        } catch (IOException e) {
            logger.warn("Failed to read file {}: {}", candidate.path(), e.getMessage(), e);
            return ProcessedCandidate.skip();
        }
    }

    private String toRelativePath(Path path) {
        return baseDirectory.relativize(path).toString().replace('\\', '/');
    }

    private boolean isTooLarge(long size) {
        return size > maxFileSize;
    }

    private boolean wouldExceedTotalSize(long totalSize, long fileSize) {
        return totalSize + fileSize > maxTotalSize;
    }

    private void logSkippedLargeFile(Path path, long size) {
        logger.debug("Skipping large file ({} bytes): {}", size, path);
    }

    private void logTotalSizeLimitReached(long totalSize) {
        logger.warn("Total content size limit reached ({} bytes). Stopping collection.", totalSize);
    }

    private record ProcessedCandidate(boolean included,
                                      boolean stopProcessing,
                                      String relativePath,
                                      String content,
                                      long size) {
        private static ProcessedCandidate included(String relativePath, String content, long size) {
            return new ProcessedCandidate(true, false, relativePath, content, size);
        }

        private static ProcessedCandidate skip() {
            return new ProcessedCandidate(false, false, null, null, 0);
        }

        private static ProcessedCandidate stop() {
            return new ProcessedCandidate(false, true, null, null, 0);
        }
    }
}
