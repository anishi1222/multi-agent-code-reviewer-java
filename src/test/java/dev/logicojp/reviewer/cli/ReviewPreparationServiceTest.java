package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.InstructionSource;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewPreparationService")
class ReviewPreparationServiceTest {

    @Test
    @DisplayName("outputDirectory計算・banner表示・instructions解決を一括で行う")
    void preparesOutputDirectoryBannerAndInstructions() {
        AtomicBoolean bannerCalled = new AtomicBoolean(false);
        AtomicReference<Path> bannerOutputDirectory = new AtomicReference<>();
        var instruction = new CustomInstruction("custom.md", "Follow project rules", InstructionSource.LOCAL_FILE, null, null);

        var service = new ReviewPreparationService(
            (agentConfigs, agentDirs, modelConfig, target, outputDirectory, reviewModel) -> {
                bannerCalled.set(true);
                bannerOutputDirectory.set(outputDirectory);
            },
            (target, options) -> List.of(instruction)
        );

        ReviewCommand.ParsedOptions options = new ReviewCommand.ParsedOptions(
            new ReviewCommand.TargetSelection.Repository("owner/repo"),
            new ReviewCommand.AgentSelection.All(),
            Path.of("./reports"),
            List.of(),
            null,
            4,
            false,
            "review-model",
            "report-model",
            "summary-model",
            "default-model",
            List.of(),
            false,
            false,
            false
        );
        ReviewTarget target = ReviewTarget.gitHub("owner/repo");
        ModelConfig modelConfig = new ModelConfig("r", "p", "s", "high", "d");
        Map<String, AgentConfig> agentConfigs = Map.of(
            "code-quality", new AgentConfig("code-quality", "Code Quality", "r", "prompt", "instruction", "", List.of(), List.of())
        );

        ReviewPreparationService.PreparedData prepared = service.prepare(
            options,
            target,
            modelConfig,
            agentConfigs,
            List.of(Path.of("agents"))
        );

        assertThat(bannerCalled.get()).isTrue();
        assertThat(prepared.outputDirectory()).isEqualTo(Path.of("./reports/owner/repo"));
        assertThat(bannerOutputDirectory.get()).isEqualTo(Path.of("./reports/owner/repo"));
        assertThat(prepared.customInstructions()).containsExactly(instruction);
    }
}
