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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

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
public class ReviewCommand implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewCommand.class);
    
    @ParentCommand
    private ReviewApp parent;
    
    @Inject
    private AgentService agentService;
    
    @Inject
    private CopilotService copilotService;
    
    @Inject
    private ReviewService reviewService;
    
    @Inject
    private ReportService reportService;
    
    @Option(
        names = {"-r", "--repo"},
        description = "Target GitHub repository (e.g., owner/repo)",
        required = true
    )
    private String repository;
    
    @Option(
        names = {"-a", "--agents"},
        description = "Comma-separated list of agent names to run",
        split = ","
    )
    private List<String> agents;
    
    @Option(
        names = {"--all"},
        description = "Run all available agents"
    )
    private boolean allAgents;
    
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
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private void execute() throws Exception {
        // Validate options
        if (!allAgents && (agents == null || agents.isEmpty())) {
            System.err.println("Error: Either --all or --agents must be specified.");
            System.exit(1);
        }
        
        // Validate GitHub token
        if (githubToken == null || githubToken.isEmpty() || githubToken.equals("${GITHUB_TOKEN}")) {
            System.err.println("Error: GitHub token is required. Set GITHUB_TOKEN environment variable or use --token option.");
            System.exit(1);
        }
        
        // Build model configuration
        ModelConfig modelConfig = buildModelConfig();
        
        // Configure agent directories
        List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
        
        // Load agent configurations
        Map<String, AgentConfig> agentConfigs;
        if (allAgents) {
            agentConfigs = agentService.loadAllAgents(agentDirs);
        } else {
            agentConfigs = agentService.loadAgents(agentDirs, agents);
        }
        
        if (agentConfigs.isEmpty()) {
            System.err.println("Error: No agents found. Check the agents directories:");
            for (Path dir : agentDirs) {
                System.err.println("  - " + dir);
            }
            System.exit(1);
        }
        
        // Apply model overrides if specified
        if (reviewModel != null) {
            for (AgentConfig config : agentConfigs.values()) {
                config.setModel(reviewModel);
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
                    results, repository, outputDirectory, modelConfig.getSummaryModel());
                System.out.println("  ✓ " + summaryPath.getFileName());
            }
            
            // Print summary
            printCompletionSummary(results);
            
        } finally {
            copilotService.shutdown();
        }
    }
    
    private ModelConfig buildModelConfig() {
        ModelConfig.Builder builder = ModelConfig.builder();
        
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
        System.out.println("  Summary: " + modelConfig.getSummaryModel());
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
