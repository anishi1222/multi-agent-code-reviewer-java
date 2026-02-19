package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.LocalFileConfig;

import java.nio.file.Path;

@FunctionalInterface
interface LocalSourceCollectorFactory {
    LocalSourceCollector create(Path directory, LocalFileConfig localFileConfig);
}