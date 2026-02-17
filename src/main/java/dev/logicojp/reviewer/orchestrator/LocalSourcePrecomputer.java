package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

final class LocalSourcePrecomputer {

    private static final Logger logger = LoggerFactory.getLogger(LocalSourcePrecomputer.class);
    private final ReviewOrchestrator.LocalSourceCollectorFactory localSourceCollectorFactory;
    private final LocalFileConfig localFileConfig;

    LocalSourcePrecomputer(ReviewOrchestrator.LocalSourceCollectorFactory localSourceCollectorFactory,
                           LocalFileConfig localFileConfig) {
        this.localSourceCollectorFactory = localSourceCollectorFactory;
        this.localFileConfig = localFileConfig;
    }

    String preComputeSourceContent(ReviewTarget target) {
        Path directory = resolveLocalDirectory(target);
        if (directory == null) {
            return null;
        }

        logPrecomputeStart(directory);
        var collection = localSourceCollectorFactory.create(directory, localFileConfig).collectAndGenerate();
        logCollectionResult(collection.fileCount(), collection.directorySummary());
        return collection.reviewContent();
    }

    private Path resolveLocalDirectory(ReviewTarget target) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> directory;
            case ReviewTarget.GitHubTarget(_) -> null;
        };
    }

    private void logPrecomputeStart(Path directory) {
        logger.info("Pre-computing source content for local directory: {}", directory);
    }

    private void logCollectionResult(int fileCount, String directorySummary) {
        logger.info("Collected {} source files from local directory", fileCount);
        logger.debug("Directory summary:\n{}", directorySummary);
    }
}