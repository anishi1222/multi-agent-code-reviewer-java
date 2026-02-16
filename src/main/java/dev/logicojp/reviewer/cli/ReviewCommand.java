package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Main review command that executes the multi-agent code review.
@Singleton
public class ReviewCommand {

    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);

    private final ModelConfig defaultModelConfig;

    private final ReviewModelConfigResolver modelConfigResolver;

    private final ReviewOptionsParser optionsParser;

    private final ReviewTargetResolver targetResolver;

    private final ReviewAgentConfigResolver agentConfigResolver;

    private final ReviewPreparationService preparationService;

    private final ReviewRunRequestFactory runRequestFactory;

    private final ReviewExecutionCoordinator executionCoordinator;

    private final CliOutput output;

    /// Target selection — sealed interface for type-safe exclusive choice.
    sealed interface TargetSelection {
        record Repository(String repository) implements TargetSelection {}
        record LocalDirectory(Path directory) implements TargetSelection {}
    }

    /// Agent selection — sealed interface for type-safe exclusive choice.
    sealed interface AgentSelection {
        record All() implements AgentSelection {}
        record Named(List<String> agents) implements AgentSelection {}
    }

    /// Parsed CLI options for a review run.
    record ParsedOptions(
        TargetSelection target,
        AgentSelection agents,
        Path outputDirectory,
        List<Path> additionalAgentDirs,
        String githubToken,
        int parallelism,
        boolean noSummary,
        String reviewModel,
        String reportModel,
        String summaryModel,
        String defaultModel,
        List<Path> instructionPaths,
        boolean noInstructions,
        boolean noPrompts,
        boolean trustTarget
    ) {}

    @Inject
    public ReviewCommand(
        ModelConfig defaultModelConfig,
        ReviewModelConfigResolver modelConfigResolver,
        ReviewOptionsParser optionsParser,
        ReviewTargetResolver targetResolver,
        ReviewAgentConfigResolver agentConfigResolver,
        ReviewPreparationService preparationService,
        ReviewRunRequestFactory runRequestFactory,
        ReviewExecutionCoordinator executionCoordinator,
        CliOutput output
    ) {
        this.defaultModelConfig = defaultModelConfig;
        this.modelConfigResolver = modelConfigResolver;
        this.optionsParser = optionsParser;
        this.targetResolver = targetResolver;
        this.agentConfigResolver = agentConfigResolver;
        this.preparationService = preparationService;
        this.runRequestFactory = runRequestFactory;
        this.executionCoordinator = executionCoordinator;
        this.output = output;
    }

    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printRun,
            logger,
            output
        );
    }

    private Optional<ParsedOptions> parseArgs(String[] args) {
        return optionsParser.parse(args);
    }

    private int executeInternal(ParsedOptions options) {
        ReviewTargetResolver.TargetAndToken targetAndToken = resolveTargetAndToken(options);
        ReviewTarget target = targetAndToken.target();
        String resolvedToken = targetAndToken.resolvedToken();
        ModelConfig modelConfig = resolveModelConfig(options);
        ReviewAgentConfigResolver.AgentResolution agentResolution = resolveAgentConfigs(options);
        List<Path> agentDirs = agentResolution.agentDirectories();
        Map<String, AgentConfig> agentConfigs = agentResolution.agentConfigs();

        ReviewPreparationService.PreparedData prepared = prepareReviewData(
            options, target, modelConfig, agentConfigs, agentDirs);
        ReviewRunExecutor.ReviewRunRequest runRequest = createRunRequest(
            options,
            target,
            resolvedToken,
            modelConfig,
            agentConfigs,
            prepared
        );

        return executeReview(agentConfigs, agentDirs, resolvedToken, runRequest);
    }

    private ReviewTargetResolver.TargetAndToken resolveTargetAndToken(ParsedOptions options) {
        return targetResolver.resolve(options.target(), options.githubToken());
    }

    private ReviewAgentConfigResolver.AgentResolution resolveAgentConfigs(ParsedOptions options) {
        return agentConfigResolver.resolve(options);
    }

    private ReviewPreparationService.PreparedData prepareReviewData(
            ParsedOptions options,
            ReviewTarget target,
            ModelConfig modelConfig,
            Map<String, AgentConfig> agentConfigs,
            List<Path> agentDirs) {
        return preparationService.prepare(options, target, modelConfig, agentConfigs, agentDirs);
    }

    private ModelConfig resolveModelConfig(ParsedOptions options) {
        return modelConfigResolver.resolve(
            defaultModelConfig,
            options.defaultModel(),
            options.reviewModel(),
            options.reportModel(),
            options.summaryModel()
        );
    }

    private ReviewRunExecutor.ReviewRunRequest createRunRequest(
            ParsedOptions options,
            ReviewTarget target,
            String resolvedToken,
            ModelConfig modelConfig,
            Map<String, AgentConfig> agentConfigs,
            ReviewPreparationService.PreparedData prepared) {
        return runRequestFactory.create(
            options,
            target,
            resolvedToken,
            modelConfig,
            agentConfigs,
            prepared.customInstructions(),
            prepared.outputDirectory()
        );
    }

    private int executeReview(Map<String, AgentConfig> agentConfigs,
                              List<Path> agentDirs,
                              String resolvedToken,
                              ReviewRunExecutor.ReviewRunRequest runRequest) {
        return executionCoordinator.execute(agentConfigs, agentDirs, resolvedToken, runRequest);
    }

}
