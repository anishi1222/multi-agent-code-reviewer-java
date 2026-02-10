package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.logicojp.reviewer.report.SummaryGenerator.resolveReasoningEffort;

/**
 * Executes a code review using the Copilot SDK with a specific agent configuration.
 */
public class ReviewAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewAgent.class);
    
    private final AgentConfig config;
    private final CopilotClient client;
    private final String githubToken;
    private final GithubMcpConfig githubMcpConfig;
    private final long timeoutMinutes;
    private final List<CustomInstruction> customInstructions;
    private final String reasoningEffort;
    private final int maxRetries;

    public ReviewAgent(AgentConfig config,
                       CopilotClient client,
                       String githubToken,
                       GithubMcpConfig githubMcpConfig,
                       long timeoutMinutes,
                       List<CustomInstruction> customInstructions,
                       String reasoningEffort,
                       int maxRetries) {
        this.config = config;
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.timeoutMinutes = timeoutMinutes;
        this.customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        this.reasoningEffort = reasoningEffort;
        this.maxRetries = maxRetries;
    }
    
    /**
     * Executes the review synchronously on the calling thread with retry support.
     * Each attempt gets the full configured timeout — the timeout is per-attempt, not cumulative.
     * @param target The target to review (GitHub repository or local directory)
     * @return ReviewResult containing the review content
     */
    public ReviewResult review(ReviewTarget target) {
        int totalAttempts = maxRetries + 1;
        ReviewResult lastResult = null;
        
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                lastResult = executeReview(target);
                
                if (lastResult.isSuccess()) {
                    if (attempt > 1) {
                        logger.info("Agent {} succeeded on attempt {}/{}", 
                            config.getName(), attempt, totalAttempts);
                    }
                    return lastResult;
                }
                
                // Review returned failure (e.g. empty content) — retry if attempts remain
                if (attempt < totalAttempts) {
                    logger.warn("Agent {} failed on attempt {}/{}: {}. Retrying...", 
                        config.getName(), attempt, totalAttempts, lastResult.getErrorMessage());
                } else {
                    logger.error("Agent {} failed on final attempt {}/{}: {}", 
                        config.getName(), attempt, totalAttempts, lastResult.getErrorMessage());
                }
                
            } catch (Exception e) {
                lastResult = ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.getDisplayName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
                
                if (attempt < totalAttempts) {
                    logger.warn("Agent {} threw exception on attempt {}/{}: {}. Retrying...", 
                        config.getName(), attempt, totalAttempts, e.getMessage());
                } else {
                    logger.error("Agent {} threw exception on final attempt {}/{}: {}", 
                        config.getName(), attempt, totalAttempts, e.getMessage(), e);
                }
            }
        }
        
        return lastResult;
    }
    
    private ReviewResult executeReview(ReviewTarget target) throws Exception {
        logger.info("Starting review with agent: {} for target: {}", 
            config.getName(), target.getDisplayName());
        
        // Java 21+: Pattern matching for switch with record patterns
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> executeLocalReview(directory, target);
            case ReviewTarget.GitHubTarget(String repository) -> executeGitHubReview(repository, target);
        };
    }
    
    private ReviewResult executeGitHubReview(String repository, ReviewTarget target) throws Exception {
        
        // Configure GitHub MCP server for repository access
        Map<String, Object> githubMcp = githubMcpConfig.toMcpServer(githubToken);
        
        // Create session with agent configuration
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        var sessionConfig = new SessionConfig()
            .setModel(config.getModel())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt))
            .setMcpServers(Map.of("github", githubMcp));

        // Explicitly set reasoning effort for reasoning models (e.g. Claude Opus)
        String effort = resolveReasoningEffort(config.getModel(), reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, config.getModel());
            sessionConfig.setReasoningEffort(effort);
        }

        var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            // Build the instruction
            String instruction = config.buildInstruction(repository);
            
            // Pass the configured timeout to sendAndWait explicitly.
            // The SDK default (60s) is far too short for reviews involving MCP tool calls.
            logger.debug("Sending instruction to agent: {} (timeout: {} min)", config.getName(), timeoutMinutes);
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(instruction), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            
            String content = response.getData().getContent();
            
            // Validate response content
            if (content == null || content.isBlank()) {
                logger.warn("Agent {} returned empty content. encryptedContent={}, toolRequests={}",
                    config.getName(),
                    response.getData().getEncryptedContent() != null ? "present" : "null",
                    response.getData().getToolRequests() != null ? response.getData().getToolRequests().size() : 0);
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(repository)
                    .success(false)
                    .errorMessage("Agent returned empty review content — model may have timed out during MCP tool calls")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            logger.info("Review completed for agent: {} (content length: {} chars)", 
                config.getName(), content.length());
            
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(repository)
                .content(content)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
                
        } finally {
            session.close();
        }
    }
    
    private ReviewResult executeLocalReview(Path localPath, ReviewTarget target) throws Exception {
        
        // Collect files from local directory
        LocalFileProvider fileProvider = new LocalFileProvider(localPath);
        var localFiles = fileProvider.collectFiles();
        String sourceContent = fileProvider.generateReviewContent(localFiles);
        String directorySummary = fileProvider.generateDirectorySummary(localFiles);
        
        logger.info("Collected source files from local directory: {}", localPath);
        logger.debug("Directory summary:\n{}", directorySummary);
        
        // Create session without MCP servers (no external tools needed for local review)
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        var sessionConfig = new SessionConfig()
            .setModel(config.getModel())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));

        // Explicitly set reasoning effort for reasoning models (e.g. Claude Opus)
        String effort = resolveReasoningEffort(config.getModel(), reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, config.getModel());
            sessionConfig.setReasoningEffort(effort);
        }

        var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            // Build the instruction with embedded source code
            String instruction = config.buildLocalInstruction(target.getDisplayName(), sourceContent);
            
            // Pass the configured timeout to sendAndWait explicitly.
            // The SDK default (60s) is far too short for complex reviews.
            logger.debug("Sending local instruction to agent: {} (timeout: {} min)", config.getName(), timeoutMinutes);
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(instruction), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            
            String content = response.getData().getContent();
            
            // Validate response content
            if (content == null || content.isBlank()) {
                logger.warn("Agent {} returned empty content for local review. encryptedContent={}, toolRequests={}",
                    config.getName(),
                    response.getData().getEncryptedContent() != null ? "present" : "null",
                    response.getData().getToolRequests() != null ? response.getData().getToolRequests().size() : 0);
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.getDisplayName())
                    .success(false)
                    .errorMessage("Agent returned empty review content")
                    .timestamp(LocalDateTime.now())
                    .build();
            }
            
            logger.info("Local review completed for agent: {} (content length: {} chars)", 
                config.getName(), content.length());
            
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(target.getDisplayName())
                .content(content)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
                
        } finally {
            session.close();
        }
    }
    
    /**
     * Builds the system prompt including custom instructions if available.
     * Each custom instruction is rendered with its scope metadata (applyTo, description)
     * from GitHub Copilot per-scope instruction files (.github/instructions/*.instructions.md).
     */
    private String buildSystemPromptWithCustomInstruction() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.buildFullSystemPrompt());
        
        if (!customInstructions.isEmpty()) {
            for (CustomInstruction instruction : customInstructions) {
                if (!instruction.isEmpty()) {
                    sb.append("\n\n");
                    sb.append(instruction.toPromptSection());
                    logger.debug("Applied custom instruction from {} to agent: {}", 
                        instruction.sourcePath(), config.getName());
                }
            }
        }
        
        return sb.toString();
    }
}
