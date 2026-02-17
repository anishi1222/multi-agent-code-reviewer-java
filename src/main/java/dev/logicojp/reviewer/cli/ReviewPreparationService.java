package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Singleton
class ReviewPreparationService {

    @FunctionalInterface
    interface BannerPrinter {
        void print(Map<String, AgentConfig> agentConfigs,
                   List<Path> agentDirs,
                   ModelConfig modelConfig,
                   ReviewTarget target,
                   Path outputDirectory,
                   String reviewModel);
    }

    @FunctionalInterface
    interface InstructionResolver {
        List<CustomInstruction> resolve(ReviewTarget target, ReviewCustomInstructionResolver.InstructionOptions options);
    }

    public record PreparedData(Path outputDirectory, List<CustomInstruction> customInstructions) {
    }

    private final BannerPrinter bannerPrinter;
    private final InstructionResolver instructionResolver;

    @Inject
    public ReviewPreparationService(ReviewOutputFormatter outputFormatter,
                                    ReviewCustomInstructionResolver customInstructionResolver) {
        this(outputFormatter::printBanner, customInstructionResolver::resolve);
    }

    ReviewPreparationService(BannerPrinter bannerPrinter, InstructionResolver instructionResolver) {
        this.bannerPrinter = bannerPrinter;
        this.instructionResolver = instructionResolver;
    }

    public PreparedData prepare(ReviewCommand.ParsedOptions options,
                                ReviewTarget target,
                                ModelConfig modelConfig,
                                Map<String, AgentConfig> agentConfigs,
                                List<Path> agentDirs) {
        Path outputDirectory = resolveOutputDirectory(options, target);

        bannerPrinter.print(agentConfigs, agentDirs, modelConfig, target, outputDirectory, options.reviewModel());

        List<CustomInstruction> customInstructions = resolveCustomInstructions(options, target);

        return createPreparedData(outputDirectory, customInstructions);
    }

    private Path resolveOutputDirectory(ReviewCommand.ParsedOptions options, ReviewTarget target) {
        return options.outputDirectory().resolve(target.repositorySubPath());
    }

    private ReviewCustomInstructionResolver.InstructionOptions buildInstructionOptions(
            ReviewCommand.ParsedOptions options) {
        return new ReviewCustomInstructionResolver.InstructionOptions(
            options.instructionPaths(),
            options.noInstructions(),
            options.noPrompts(),
            options.trustTarget()
        );
    }

    private List<CustomInstruction> resolveCustomInstructions(ReviewCommand.ParsedOptions options,
                                                              ReviewTarget target) {
        return instructionResolver.resolve(target, buildInstructionOptions(options));
    }

    private PreparedData createPreparedData(Path outputDirectory,
                                            List<CustomInstruction> customInstructions) {
        return new PreparedData(outputDirectory, customInstructions);
    }
}
