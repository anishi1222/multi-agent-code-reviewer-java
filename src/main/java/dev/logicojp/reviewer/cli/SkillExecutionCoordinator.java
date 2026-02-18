package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.SkillService;
import dev.logicojp.reviewer.skill.SkillResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
class SkillExecutionCoordinator {

    @FunctionalInterface
    interface Initializer {
        void initialize(String resolvedToken);
    }

    @FunctionalInterface
    interface SkillRunner {
        SkillResult run(String skillId, Map<String, String> parameters, String resolvedToken, String model,
                        long timeoutMinutes)
            throws ExecutionException, TimeoutException, InterruptedException;
    }

    @FunctionalInterface
    interface Shutdowner {
        void shutdown();
    }

    private final Initializer initializer;
    private final SkillRunner skillRunner;
    private final Shutdowner shutdowner;
    private final CliOutput output;

    @Inject
    public SkillExecutionCoordinator(CopilotService copilotService,
                                     SkillService skillService,
                                     CliOutput output) {
        this(
            copilotService::initializeOrThrow,
            (skillId, parameters, resolvedToken, model, timeoutMinutes) ->
                skillService.executeSkill(skillId, parameters, resolvedToken, model)
                    .get(timeoutMinutes, TimeUnit.MINUTES),
            copilotService::shutdown,
            output
        );
    }

    SkillExecutionCoordinator(Initializer initializer,
                              SkillRunner skillRunner,
                              Shutdowner shutdowner,
                              CliOutput output) {
        this.initializer = initializer;
        this.skillRunner = skillRunner;
        this.shutdowner = shutdowner;
        this.output = output;
    }

    public int execute(String skillId,
                       Map<String, String> parameters,
                       String resolvedToken,
                       String model,
                       long timeoutMinutes) {
        try {
            return LifecycleRunner.executeWithLifecycle(
                () -> initializer.initialize(resolvedToken),
                () -> runUnchecked(skillId, parameters, resolvedToken, model, timeoutMinutes),
                shutdowner::shutdown
            );
        } catch (SkillRunExecutionException e) {
            throw toExecutionFailure((ExecutionException) e.getCause());
        } catch (SkillRunTimeoutException e) {
            throw toExecutionFailure((TimeoutException) e.getCause());
        } catch (SkillRunInterruptedException e) {
            Thread.currentThread().interrupt();
            throw toInterruptedFailure((InterruptedException) e.getCause());
        }
    }

    private int runUnchecked(String skillId,
                             Map<String, String> parameters,
                             String resolvedToken,
                             String model,
                             long timeoutMinutes) {
        try {
            return runAndHandleResult(skillId, parameters, resolvedToken, model, timeoutMinutes);
        } catch (ExecutionException e) {
            throw new SkillRunExecutionException(e);
        } catch (TimeoutException e) {
            throw new SkillRunTimeoutException(e);
        } catch (InterruptedException e) {
            throw new SkillRunInterruptedException(e);
        }
    }

    private static final class SkillRunExecutionException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private SkillRunExecutionException(ExecutionException cause) {
            super(cause);
        }
    }

    private static final class SkillRunTimeoutException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private SkillRunTimeoutException(TimeoutException cause) {
            super(cause);
        }
    }

    private static final class SkillRunInterruptedException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private SkillRunInterruptedException(InterruptedException cause) {
            super(cause);
        }
    }

    private int runAndHandleResult(String skillId,
                                   Map<String, String> parameters,
                                   String resolvedToken,
                                   String model,
                                   long timeoutMinutes)
        throws ExecutionException, TimeoutException, InterruptedException {
        printExecutionStart(skillId, parameters);
        SkillResult result = skillRunner.run(skillId, parameters, resolvedToken, model, timeoutMinutes);
        return toExitCode(result);
    }

    private void printExecutionStart(String skillId, Map<String, String> parameters) {
        output.println("Executing skill: " + skillId);
        output.println("Parameters: " + parameters.keySet());
    }

    private int toExitCode(SkillResult result) {
        if (result.success()) {
            return handleSuccessfulResult(result);
        }
        return handleFailedResult(result);
    }

    private int handleSuccessfulResult(SkillResult result) {
        output.println("=== Skill Result ===\n");
        output.println(result.content());
        return ExitCodes.OK;
    }

    private int handleFailedResult(SkillResult result) {
        output.errorln("Skill execution failed: " + result.errorMessage());
        return ExitCodes.SOFTWARE;
    }

    private CopilotCliException toExecutionFailure(Exception cause) {
        return new CopilotCliException("Skill execution failed", cause);
    }

    private CopilotCliException toInterruptedFailure(InterruptedException cause) {
        return new CopilotCliException("Skill execution interrupted", cause);
    }
}
