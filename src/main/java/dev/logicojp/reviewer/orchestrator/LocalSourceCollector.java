package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.target.LocalFileProvider;

@FunctionalInterface
interface LocalSourceCollector {
    LocalFileProvider.CollectionResult collectAndGenerate();
}