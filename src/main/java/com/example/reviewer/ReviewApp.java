package com.example.reviewer;

import com.example.reviewer.agent.AgentConfig;
import com.example.reviewer.agent.AgentConfigLoader;
import com.example.reviewer.config.ModelConfig;
import com.example.reviewer.orchestrator.ReviewOrchestrator;
import com.example.reviewer.report.ReportGenerator;
import com.example.reviewer.report.ReviewResult;
import com.example.reviewer.report.SummaryGenerator;
import com.github.copilot.sdk.CopilotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Multi-Agent Code Reviewer CLI Application.
 * 
 * Uses GitHub Copilot SDK to run multiple AI agents in parallel for code review,
 * and generates individual reports and an executive summary.
 * 
 * Supports agent definitions in:
 * - YAML format (.yaml, .yml)
 * - GitHub Copilot agent format (.agent.md)
 */
@Command(
    name = "review",
    mixinStandardHelpOptions = true,
    version = "Multi-Agent Reviewer 1.0.0",
    description = "Run multiple AI agents to review a GitHub repository and generate reports."
)
public class ReviewApp implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewApp.class);
    
    @Option(
        names = {"-r", "--repo"},
        description = "Target GitHub repository (e.g., owner/repo)"
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
        names = {"--list"},
        description = "List all available agents and exit"
    )
    private boolean listAgents;
    
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
        description = "LLM model for executive summary generation (default: claude-sonnet-4)",
        defaultValue = "claude-sonnet-4"
    )
    private String summaryModel;
    
    @Option(
        names = {"--model"},
        description = "Default LLM model for all stages (can be overridden by specific model options)"
    )
    private String defaultModel;
    
    @Override
    public Integer call() {
        try {
            return execute();
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
    
    private Integer execute() throws Exception {
        // Build list of agent directories
        List<Path> agentDirs = buildAgentDirectories();
        AgentConfigLoader configLoader = new AgentConfigLoader(agentDirs);
        
        // Handle --list option
        if (listAgents) {
            return listAvailableAgents(configLoader, agentDirs);
        }
        
        // Validate repository is specified
        if (repository == null || repository.isEmpty()) {
            System.err.println("Error: --repo is required.");
            System.err.println("Use --help for usage information.");
            return 1;
        }
        
        // Validate options
        if (!allAgents && (agents == null || agents.isEmpty())) {
            System.err.println("Error: Either --all or --agents must be specified.");
            System.err.println("Use --help for usage information.");
            return 1;
        }
        
        // Validate GitHub token
        if (githubToken == null || githubToken.isEmpty() || githubToken.equals("${GITHUB_TOKEN}")) {
            System.err.println("Error: GitHub token is required. Set GITHUB_TOKEN environment variable or use --token option.");
            return 1;
        }
        
        // Build model configuration
        ModelConfig modelConfig = buildModelConfig();
        
        // Load agent configurations
        Map<String, AgentConfig> agentConfigs;
        if (allAgents) {
            agentConfigs = configLoader.loadAllAgents();
        } else {
            agentConfigs = configLoader.loadAgents(agents);
        }
        
        if (agentConfigs.isEmpty()) {
            System.err.println("Error: No agents found. Check the agents directories:");
            for (Path dir : agentDirs) {
                System.err.println("  - " + dir);
            }
            return 1;
        }
        
        // Apply model overrides if specified
        if (reviewModel != null) {
            for (AgentConfig config : agentConfigs.values()) {
                config.setModel(reviewModel);
            }
        }
        
        printBanner(agentConfigs, agentDirs, modelConfig);
        
        // Initialize Copilot client
        try (CopilotClient client = new CopilotClient()) {
            client.start().get();
            
            // Execute reviews in parallel
            System.out.println("Starting reviews...");
            ReviewOrchestrator orchestrator = new ReviewOrchestrator(client, githubToken, parallelism);
            List<ReviewResult> results = orchestrator.executeReviews(agentConfigs, repository);
            orchestrator.shutdown();
            
            // Generate individual reports
            System.out.println("\nGenerating reports...");
            ReportGenerator reportGenerator = new ReportGenerator(outputDirectory);
            List<Path> reports = reportGenerator.generateReports(results);
            
            for (Path report : reports) {
                System.out.println("  ✓ " + report.getFileName());
            }
            
            // Generate executive summary
            if (!noSummary) {
                System.out.println("\nGenerating executive summary...");
                SummaryGenerator summaryGenerator = new SummaryGenerator(
                    outputDirectory, client, modelConfig.getSummaryModel());
                Path summaryPath = summaryGenerator.generateSummary(results, repository);
                System.out.println("  ✓ " + summaryPath.getFileName());
            }
            
            // Print summary
            printCompletionSummary(results);
            
            return 0;
        }
    }
    
    private List<Path> buildAgentDirectories() {
        List<Path> dirs = new ArrayList<>();
        
        // Default directories
        Path defaultAgentsDir = Paths.get("./agents");
        Path githubAgentsDir = Paths.get("./.github/agents");
        
        if (Files.exists(defaultAgentsDir)) {
            dirs.add(defaultAgentsDir);
        }
        if (Files.exists(githubAgentsDir)) {
            dirs.add(githubAgentsDir);
        }
        
        // Additional directories specified via CLI
        if (additionalAgentDirs != null) {
            dirs.addAll(additionalAgentDirs);
        }
        
        // If no directories found, add default for error message
        if (dirs.isEmpty()) {
            dirs.add(defaultAgentsDir);
        }
        
        return dirs;
    }
    
    private ModelConfig buildModelConfig() {
        ModelConfig.Builder builder = ModelConfig.builder();
        
        // Set default model if specified
        if (defaultModel != null) {
            builder.defaultModel(defaultModel);
        }
        
        // Override with specific models if specified
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
    
    private Integer listAvailableAgents(AgentConfigLoader configLoader, List<Path> agentDirs) throws Exception {
        List<String> availableAgents = configLoader.listAvailableAgents();
        
        System.out.println("Agent directories:");
        for (Path dir : agentDirs) {
            System.out.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        System.out.println();
        
        if (availableAgents.isEmpty()) {
            System.out.println("No agents found.");
            return 0;
        }
        
        System.out.println("Available agents:");
        for (String agent : availableAgents) {
            System.out.println("  - " + agent);
        }
        
        return 0;
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ReviewApp()).execute(args);
        System.exit(exitCode);
    }
}
