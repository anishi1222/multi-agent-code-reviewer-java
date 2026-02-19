package dev.logicojp.reviewer.orchestrator;

import io.micronaut.core.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

record ExecutorResources(
    @Nullable ExecutorService executorService,
    ExecutorService agentExecutionExecutor,
    ScheduledExecutorService sharedScheduler,
    Semaphore concurrencyLimit
) {
    ExecutorResources {
        agentExecutionExecutor = Objects.requireNonNull(agentExecutionExecutor);
        sharedScheduler = Objects.requireNonNull(sharedScheduler);
        concurrencyLimit = Objects.requireNonNull(concurrencyLimit);
    }
}