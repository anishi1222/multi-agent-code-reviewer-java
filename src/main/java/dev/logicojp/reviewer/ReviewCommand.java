package dev.logicojp.reviewer;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.ReportService;
import dev.logicojp.reviewer.service.ReviewService;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.GithubTokenResolver;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeGenerator;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Main review command that executes the multi-agent code review.
 */
@Command(
    name = "run",
    description = "Execute a multi-agent code review on a GitHub repository."
)
public class ReviewCommand implements Runnable, IExitCodeGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);
    
    @ParentCommand
    private ReviewApp parent;

    @Spec
    private CommandSpec spec;
    
    private final AgentService agentService;
    
    private final CopilotService copilotService;
    
    private final ReviewService reviewService;
    
    private final ReportService reportService;

    private final ModelConfig defaultModelConfig;

    private int exitCode = CommandLine.ExitCode.OK;
    
    /**
     * Target selection - either GitHub repository or local directory.
     */
    static class TargetSelection {
        @Option(
            names = {"-r", "--repo"},
            description = "Target GitHub repository (e.g., owner/repo)"
        )
        private String repository;

        @Option(
            names = {"-l", "--local"},
            description = "Target local directory path"
        )
        private Path localDirectory;
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    private TargetSelection targetSelection;
    
    static class AgentSelection {
        @Option(
            names = {"--all"},
            description = "Run all available agents"
        )
        private boolean allAgents;

        @Option(
            names = {"-a", "--agents"},
            description = "Comma-separated list of agent names to run",
            split = ","
        )
        private List<String> agents;
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    private AgentSelection agentSelection;
    
    @Option(
        names = {"-o", "--output"},
        description = "Output directory for reports (default: ./report)",
        defaultValue = "./report"
    )
    private Path outputDirectory;
    
    @Option(
        names = {"--agents-dir"},
        description = "Additional directory for agent definitions. Can be specified multiple times.",
        arity = "1..*"
    )
    private List<Path> additionalAgentDirs;
    
    @Option(
        names = {"--token"},
        description = "GitHub token (or set GITHUB_TOKEN env variable)",
        defaultValue = "${GITHUB_TOKEN}"
    )
    private String githubToken;
    
    @Option(
        names = {"--parallelism"},
        description = "Number of agents to run in parallel (default: 4)",
        defaultValue = "4"
    )
    private int parallelism;
    
    @Option(
        names = {"--no-summary"},
        description = "Skip generating executive summary"
    )
    private boolean noSummary;
    
    // LLM Model options
    @Option(
        names = {"--review-model"},
        description = "LLM model for code review (default: agent's configured model or claude-sonnet-4)"
    )
    private String reviewModel;
    
    @Option(
        names = {"--report-model"},
        description = "LLM model for report generation (default: same as review-model)"
    )
    private String reportModel;
    
    @Option(
        names = {"--summary-model"},
        description = "LLM model for executive summary generation (default: from application.yml)"
    )
    private String summaryModel;
    
    @Option(
        names = {"--model"},
        description = "Default LLM model for all stages (can be overridden by specific model options)"
    )
    private String defaultModel;
    
    // Custom instruction options
    @Option(
        names = {"--instructions"},
        description = "Path to custom instruction file (Markdown). Can be specified multiple times.",
        arity = "1..*"
    )
    private List<Path> instructionPaths;
    
    @Option(
        names = {"--no-instructions"},
        description = "Disable automatic loading of custom instructions"
    )
    private boolean noInstructions;

    @Inject
    public ReviewCommand(
        AgentService agentService,
        CopilotService copilotService,
        ReviewService reviewService,
        ReportService reportService,
        ModelConfig defaultModelConfig
    ) {
        this.agentService = agentService;
        this.copilotService = copilotService;
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.defaultModelConfig = defaultModelConfig;
    }
    
    @Override
    public void run() {
        try {
            execute();
        } catch (ParameterException e) {
            exitCode = CommandLine.ExitCode.USAGE;
            spec.commandLine().getErr().println(e.getMessage());
            spec.commandLine().usage(spec.commandLine().getErr());
        } catch (Exception e) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            logger.error("Execution failed: {}", e.getMessage(), e);
            spec.commandLine().getErr().println("Error: " + e.getMessage());
            e.printStackTrace(spec.commandLine().getErr());
        }
    }

    public int getExitCode() {
        return exitCode;
    }
    
    private void execute() throws Exception {
        if (agentSelection == null) {
            throw new ParameterException(spec.commandLine(), "Either --all or --agents must be specified.");
        }

        if (targetSelection == null) {
            throw new ParameterException(spec.commandLine(), "Either --repo or --local must be specified.");
        }

        // Build review target
        ReviewTarget target;
        GithubTokenResolver tokenResolver = new GithubTokenResolver();
        String resolvedToken = tokenResolver.resolve(githubToken).orElse(null);
        if (targetSelection.repository != null) {
            target = ReviewTarget.gitHub(targetSelection.repository);
            
            // Validate GitHub token for repository access
            if (resolvedToken == null || resolvedToken.isBlank()) {
                throw new ParameterException(
                    spec.commandLine(),
                    "GitHub token is required for repository review. Set GITHUB_TOKEN, use --token, or login with `gh auth login`."
                );
            }
        } else if (targetSelection.localDirectory != null) {
            Path localPath = targetSelection.localDirectory.toAbsolutePath();
            if (!Files.exists(localPath)) {
                throw new ParameterException(
                    spec.commandLine(),
                    "Local directory does not exist: " + localPath
                );
            }
            if (!Files.isDirectory(localPath)) {
                throw new ParameterException(
                    spec.commandLine(),
                    "Path is not a directory: " + localPath
                );
            }
            target = ReviewTarget.local(localPath);
        } else {
            throw new ParameterException(spec.commandLine(), "Either --repo or --local must be specified.");
        }
        
        // Build model configuration
        ModelConfig modelConfig = buildModelConfig();
        
        // Configure agent directories
        List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
        
        // Load agent configurations
        Map<String, AgentConfig> agentConfigs;
        if (agentSelection.allAgents) {
            agentConfigs = agentService.loadAllAgents(agentDirs);
        } else {
            agentConfigs = agentService.loadAgents(agentDirs, agentSelection.agents);
        }
        
        if (agentConfigs.isEmpty()) {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            spec.commandLine().getErr().println("Error: No agents found. Check the agents directories:");
            for (Path dir : agentDirs) {
                spec.commandLine().getErr().println("  - " + dir);
            }
            return;
        }
        
        // Apply model overrides if specified
        if (reviewModel != null) {
            for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
                entry.setValue(entry.getValue().withModel(reviewModel));
            }
        }
        
        printBanner(agentConfigs, agentDirs, modelConfig, target);
        
        // Load custom instructions
        String customInstruction = loadCustomInstructions(target);
        
        // Execute reviews using the Copilot service
        copilotService.initialize(resolvedToken);
        
        try {
            System.out.println("Starting reviews...");
            List<ReviewResult> results = reviewService.executeReviews(
                agentConfigs, target, resolvedToken, parallelism, customInstruction);
            
            // Generate individual reports
            System.out.println("\nGenerating reports...");
            List<Path> reports = reportService.generateReports(results, outputDirectory);
            
            for (Path report : reports) {
                System.out.println("  ✓ " + report.getFileName());
            }
            
            // Generate executive summary
            if (!noSummary) {
                System.out.println("\nGenerating executive summary...");
                Path summaryPath = reportService.generateSummary(
                    results, target.getDisplayName(), outputDirectory, modelConfig.summaryModel());
                System.out.println("  ✓ " + summaryPath.getFileName());
            }
            
            // Print summary
            printCompletionSummary(results);
            
        } finally {
            copilotService.shutdown();
        }
    }
    
    private ModelConfig buildModelConfig() {
        ModelConfig baseConfig = defaultModelConfig != null ? defaultModelConfig : new ModelConfig();
        ModelConfig.Builder builder = ModelConfig.builder()
            .reviewModel(baseConfig.reviewModel())
            .reportModel(baseConfig.reportModel())
            .summaryModel(baseConfig.summaryModel());

        if (defaultModel != null) {
            builder.defaultModel(defaultModel);
        }
        if (reviewModel != null) {
            builder.reviewModel(reviewModel);
        }
        if (reportModel != null) {
            builder.reportModel(reportModel);
        }
        if (summaryModel != null) {
            builder.summaryModel(summaryModel);
        }

        return builder.build();
    }
    
    /**
     * Loads custom instructions from specified paths or target directory.
     */
    private String loadCustomInstructions(ReviewTarget target) {
        if (noInstructions) {
            logger.info("Custom instructions disabled by --no-instructions flag");
            return null;
        }
        
        StringBuilder combined = new StringBuilder();
        
        // Load from explicitly specified paths
        if (instructionPaths != null && !instructionPaths.isEmpty()) {
            for (Path path : instructionPaths) {
                try {
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        String content = Files.readString(path);
                        if (!content.isBlank()) {
                            if (!combined.isEmpty()) {
                                combined.append("\n\n---\n\n");
                            }
                            combined.append("<!-- Source: ").append(path).append(" -->\n");
                            combined.append(content.trim());
                            System.out.println("  ✓ Loaded instructions: " + path);
                        }
                    } else {
                        logger.warn("Instruction file not found: {}", path);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to read instruction file {}: {}", path, e.getMessage());
                }
            }
        }
        
        // Also try to load from target directory (for local targets)
        if (target.isLocal()) {
            CustomInstructionLoader loader = new CustomInstructionLoader();
            loader.loadForTarget(target).ifPresent(instruction -> {
                if (!combined.isEmpty()) {
                    combined.append("\n\n---\n\n");
                }
                combined.append(instruction.content());
                System.out.println("  ✓ Loaded instructions from target: " + instruction.sourcePath());
            });
        }
        
        String result = combined.toString().trim();
        return result.isEmpty() ? null : result;
    }
    
    private void printBanner(Map<String, AgentConfig> agentConfigs, 
                             List<Path> agentDirs, ModelConfig modelConfig,
                             ReviewTarget target) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Multi-Agent Code Reviewer                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Target: " + target.getDisplayName() + 
            (target.isLocal() ? " (local)" : " (GitHub)"));
        System.out.println("Agents: " + agentConfigs.keySet());
        System.out.println("Output: " + outputDirectory.toAbsolutePath());
        System.out.println();
        System.out.println("Agent directories:");
        for (Path dir : agentDirs) {
            System.out.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        System.out.println();
        System.out.println("Models:");
        System.out.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        System.out.println("  Summary: " + modelConfig.summaryModel());
        System.out.println();
    }
    
    private void printCompletionSummary(List<ReviewResult> results) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("Review completed!");
        System.out.println("  Total agents: " + results.size());
        System.out.println("  Successful: " + results.stream().filter(ReviewResult::isSuccess).count());
        System.out.println("  Failed: " + results.stream().filter(r -> !r.isSuccess()).count());
        System.out.println("  Reports: " + outputDirectory.toAbsolutePath());
        System.out.println("════════════════════════════════════════════════════════════");
    }
}
