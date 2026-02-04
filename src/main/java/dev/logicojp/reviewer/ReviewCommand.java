package dev.logicojp.reviewer;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.ReportService;
import dev.logicojp.reviewer.service.ReviewService;
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
    
    @Inject
    private AgentService agentService;
    
    @Inject
    private CopilotService copilotService;
    
    @Inject
    private ReviewService reviewService;
    
    @Inject
    private ReportService reportService;

    @Inject
    private ModelConfig defaultModelConfig;

    private int exitCode = CommandLine.ExitCode.OK;
    
    @Option(
        names = {"-r", "--repo"},
        description = "Target GitHub repository (e.g., owner/repo)",
        required = true
    )
    private String repository;
    
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
        description = "LLM model for executive summary generation",
        defaultValue = "claude-sonnet-4"
    )
    private String summaryModel;
    
    @Option(
        names = {"--model"},
        description = "Default LLM model for all stages (can be overridden by specific model options)"
    )
    private String defaultModel;
    
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
        }
    }

    public int getExitCode() {
        return exitCode;
    }
    
    private void execute() throws Exception {
        if (agentSelection == null) {
            throw new ParameterException(spec.commandLine(), "Either --all or --agents must be specified.");
        }
        
        // Validate GitHub token
        if (githubToken == null || githubToken.isEmpty() || githubToken.equals("${GITHUB_TOKEN}")) {
            throw new ParameterException(
                spec.commandLine(),
                "GitHub token is required. Set GITHUB_TOKEN environment variable or use --token option."
            );
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
        
        printBanner(agentConfigs, agentDirs, modelConfig);
        
        // Execute reviews using the Copilot service
        copilotService.initialize();
        
        try {
            System.out.println("Starting reviews...");
            List<ReviewResult> results = reviewService.executeReviews(
                agentConfigs, repository, githubToken, parallelism);
            
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
                    results, repository, outputDirectory, modelConfig.summaryModel());
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
    
    private void printBanner(Map<String, AgentConfig> agentConfigs, 
                             List<Path> agentDirs, ModelConfig modelConfig) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Multi-Agent Code Reviewer                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Repository: " + repository);
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
