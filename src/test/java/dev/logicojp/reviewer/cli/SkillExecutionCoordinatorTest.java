package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.skill.SkillResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillExecutionCoordinator")
class SkillExecutionCoordinatorTest {

    @Test
    @DisplayName("成功時はOKを返しshutdownされる")
    void returnsOkOnSuccess() {
        AtomicInteger initCount = new AtomicInteger();
        AtomicInteger shutdownCount = new AtomicInteger();

        var coordinator = new SkillExecutionCoordinator(
            token -> initCount.incrementAndGet(),
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> SkillResult.success(skillId, "done"),
            shutdownCount::incrementAndGet,
            cliOutput()
        );

        int exit = coordinator.execute("scan", Map.of("env", "prod"), "token", "gpt-5", 1);

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(initCount.get()).isEqualTo(1);
        assertThat(shutdownCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("失敗結果時はSOFTWAREを返す")
    void returnsSoftwareOnFailureResult() {
        var coordinator = new SkillExecutionCoordinator(
            token -> {
            },
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> SkillResult.failure(skillId, "failure"),
            () -> {
            },
            cliOutput()
        );

        int exit = coordinator.execute("scan", Map.of(), "token", "gpt-5", 1);

        assertThat(exit).isEqualTo(ExitCodes.SOFTWARE);
    }

    @Test
    @DisplayName("ExecutionExceptionはCopilotCliExceptionに変換")
    void wrapsExecutionException() {
        var coordinator = new SkillExecutionCoordinator(
            token -> {
            },
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> {
                throw new ExecutionException("boom", new RuntimeException("boom"));
            },
            () -> {
            },
            cliOutput()
        );

        assertThatThrownBy(() -> coordinator.execute("scan", Map.of(), "token", "gpt-5", 1))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("Skill execution failed");
    }

    @Test
    @DisplayName("TimeoutExceptionはCopilotCliExceptionに変換")
    void wrapsTimeoutException() {
        var coordinator = new SkillExecutionCoordinator(
            token -> {
            },
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> {
                throw new TimeoutException("timeout");
            },
            () -> {
            },
            cliOutput()
        );

        assertThatThrownBy(() -> coordinator.execute("scan", Map.of(), "token", "gpt-5", 1))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("Skill execution failed");
    }

    @Test
    @DisplayName("例外時もshutdownが呼ばれる")
    void callsShutdownOnException() {
        AtomicBoolean shutdownCalled = new AtomicBoolean(false);
        var coordinator = new SkillExecutionCoordinator(
            token -> {
            },
            (skillId, parameters, resolvedToken, model, timeoutMinutes) -> {
                throw new ExecutionException("boom", new RuntimeException("boom"));
            },
            () -> shutdownCalled.set(true),
            cliOutput()
        );

        assertThatThrownBy(() -> coordinator.execute("scan", Map.of(), "token", "gpt-5", 1))
            .isInstanceOf(CopilotCliException.class);
        assertThat(shutdownCalled.get()).isTrue();
    }

    private static CliOutput cliOutput() {
        return new CliOutput(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
    }
}
