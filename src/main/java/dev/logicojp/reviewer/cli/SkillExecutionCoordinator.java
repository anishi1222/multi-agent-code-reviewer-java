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
public class SkillExecutionCoordinator {

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
            initializer.initialize(resolvedToken);
            return runAndHandleResult(skillId, parameters, resolvedToken, model, timeoutMinutes);
        } catch (ExecutionException | TimeoutException e) {
            throw toExecutionFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw toInterruptedFailure(e);
        } finally {
            shutdowner.shutdown();
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
        if (result.isSuccess()) {
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
