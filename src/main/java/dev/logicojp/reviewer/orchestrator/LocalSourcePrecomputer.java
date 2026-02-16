package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;

import java.nio.file.Path;

final class LocalSourcePrecomputer {

    private final Logger logger;
    private final ReviewOrchestrator.LocalSourceCollectorFactory localSourceCollectorFactory;
    private final LocalFileConfig localFileConfig;

    LocalSourcePrecomputer(Logger logger,
                           ReviewOrchestrator.LocalSourceCollectorFactory localSourceCollectorFactory,
                           LocalFileConfig localFileConfig) {
        this.logger = logger;
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
        if (target instanceof ReviewTarget.LocalTarget(Path directory)) {
            return directory;
        }
        return null;
    }

    private void logPrecomputeStart(Path directory) {
        logger.info("Pre-computing source content for local directory: {}", directory);
    }

    private void logCollectionResult(int fileCount, String directorySummary) {
        logger.info("Collected {} source files from local directory", fileCount);
        logger.debug("Directory summary:\n{}", directorySummary);
    }
}