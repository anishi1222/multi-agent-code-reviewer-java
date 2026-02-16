package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillExecutionPreparation")
class SkillExecutionPreparationTest {

    @Test
    @DisplayName("--list指定時はlistOnlyで返す")
    void returnsListOnlyWhenListOptionEnabled() {
        AtomicBoolean registered = new AtomicBoolean(false);
        var service = new SkillExecutionPreparation(
            additionalDirs -> Map.of("a", agentConfig("a")),
            agents -> registered.set(true),
            token -> java.util.Optional.of("token"),
            skillId -> true
        );

        SkillExecutionPreparation.PreparationResult result = service.prepare(options(null, true, List.of()));

        assertThat(registered.get()).isTrue();
        assertThat(result.listOnly()).isTrue();
        assertThat(result.parameters()).isEmpty();
    }

    @Test
    @DisplayName("通常実行時はtokenとparametersを返す")
    void returnsResolvedTokenAndParameters() {
        var service = new SkillExecutionPreparation(
            additionalDirs -> Map.of("a", agentConfig("a")),
            agents -> {
            },
            token -> java.util.Optional.of("resolved-token"),
            skillId -> true
        );

        SkillExecutionPreparation.PreparationResult result = service.prepare(
            options("scan-security", false, List.of("env=prod", "region=jp"))
        );

        assertThat(result.listOnly()).isFalse();
        assertThat(result.resolvedToken()).isEqualTo("resolved-token");
        assertThat(result.parameters()).containsEntry("env", "prod").containsEntry("region", "jp");
    }

    @Test
    @DisplayName("skill ID未指定時はバリデーションエラー")
    void throwsWhenSkillIdMissing() {
        var service = new SkillExecutionPreparation(
            additionalDirs -> Map.of("a", agentConfig("a")),
            agents -> {
            },
            token -> java.util.Optional.of("resolved-token"),
            skillId -> true
        );

        assertThatThrownBy(() -> service.prepare(options(" ", false, List.of())))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Skill ID required");
    }

    @Test
    @DisplayName("skill未存在時はバリデーションエラー")
    void throwsWhenSkillNotFound() {
        var service = new SkillExecutionPreparation(
            additionalDirs -> Map.of("a", agentConfig("a")),
            agents -> {
            },
            token -> java.util.Optional.of("resolved-token"),
            skillId -> false
        );

        assertThatThrownBy(() -> service.prepare(options("unknown", false, List.of())))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Skill not found");
    }

    @Test
    @DisplayName("agent読込のIOExceptionはUncheckedIOExceptionに変換")
    void wrapsIoExceptionFromAgentLoad() {
        var service = new SkillExecutionPreparation(
            additionalDirs -> {
                throw new IOException("boom");
            },
            agents -> {
            },
            token -> java.util.Optional.of("resolved-token"),
            skillId -> true
        );

        assertThatThrownBy(() -> service.prepare(options("scan", false, List.of())))
            .isInstanceOf(UncheckedIOException.class)
            .hasMessageContaining("Failed to load agents");
    }

    @Test
    @DisplayName("不正なparameter形式はバリデーションエラー")
    void throwsWhenParameterFormatInvalid() {
        var service = new SkillExecutionPreparation(
            additionalDirs -> Map.of("a", agentConfig("a")),
            agents -> {
            },
            token -> java.util.Optional.of("resolved-token"),
            skillId -> true
        );

        assertThatThrownBy(() -> service.prepare(options("scan", false, List.of("invalid"))))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Expected 'key=value'");
    }

    private static SkillCommand.ParsedOptions options(String skillId, boolean list, List<String> params) {
        return new SkillCommand.ParsedOptions(
            skillId,
            params,
            null,
            "gpt-5",
            List.of(Path.of("agents")),
            list
        );
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(name, name, "gpt-5", "prompt", "instruction", "", List.of(), List.of());
    }
}
