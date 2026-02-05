package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final String customInstruction;
    
    public ReviewAgent(AgentConfig config,
                       CopilotClient client,
                       String githubToken,
                       GithubMcpConfig githubMcpConfig,
                       long timeoutMinutes) {
        this(config, client, githubToken, githubMcpConfig, timeoutMinutes, null);
    }
    
    public ReviewAgent(AgentConfig config,
                       CopilotClient client,
                       String githubToken,
                       GithubMcpConfig githubMcpConfig,
                       long timeoutMinutes,
                       String customInstruction) {
        this.config = config;
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.timeoutMinutes = timeoutMinutes;
        this.customInstruction = customInstruction;
    }
    
    /**
     * Executes the review for the given target.
     * @param target The target to review (GitHub repository or local directory)
     * @return ReviewResult containing the review content
     */
    public CompletableFuture<ReviewResult> review(ReviewTarget target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeReview(target);
            } catch (Exception e) {
                logger.error("Review failed for agent {}: {}", config.getName(), e.getMessage(), e);
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.getDisplayName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }
    
    private ReviewResult executeReview(ReviewTarget target) throws Exception {
        logger.info("Starting review with agent: {} for target: {}", 
            config.getName(), target.getDisplayName());
        
        if (target.isLocal()) {
            return executeLocalReview(target);
        } else {
            return executeGitHubReview(target);
        }
    }
    
    private ReviewResult executeGitHubReview(ReviewTarget target) throws Exception {
        String repository = target.getRepository()
            .orElseThrow(() -> new IllegalStateException("GitHub target must have repository"));
        
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
        
        var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            // Build the review prompt
            String reviewPrompt = config.buildReviewPrompt(repository);
            
            // Execute the review
            logger.debug("Sending review prompt to agent: {}", config.getName());
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(reviewPrompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            
            String content = response.getData().getContent();
            logger.info("Review completed for agent: {}", config.getName());
            
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
    
    private ReviewResult executeLocalReview(ReviewTarget target) throws Exception {
        Path localPath = target.getLocalPath()
            .orElseThrow(() -> new IllegalStateException("Local target must have path"));
        
        // Collect files from local directory
        LocalFileProvider fileProvider = new LocalFileProvider(localPath);
        String sourceContent = fileProvider.generateReviewContent();
        String directorySummary = fileProvider.generateDirectorySummary();
        
        logger.info("Collected source files from local directory: {}", localPath);
        logger.debug("Directory summary:\n{}", directorySummary);
        
        // Create session without MCP servers (no external tools needed for local review)
        String systemPrompt = buildSystemPromptWithCustomInstruction();
        var sessionConfig = new SessionConfig()
            .setModel(config.getModel())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));
        
        var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            // Build the review prompt with embedded source code
            String reviewPrompt = config.buildLocalReviewPrompt(target.getDisplayName(), sourceContent);
            
            // Execute the review
            logger.debug("Sending local review prompt to agent: {}", config.getName());
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(reviewPrompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            
            String content = response.getData().getContent();
            logger.info("Local review completed for agent: {}", config.getName());
            
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
     */
    private String buildSystemPromptWithCustomInstruction() {
        StringBuilder sb = new StringBuilder();
        sb.append(config.buildFullSystemPrompt());
        
        if (customInstruction != null && !customInstruction.isBlank()) {
            sb.append("\n\n");
            sb.append("## カスタムインストラクション\n\n");
            sb.append("以下のプロジェクト固有の指示に従ってレビューを行ってください:\n\n");
            sb.append(customInstruction.trim());
            sb.append("\n");
            logger.debug("Applied custom instruction to agent: {}", config.getName());
        }
        
        return sb.toString();
    }
}
