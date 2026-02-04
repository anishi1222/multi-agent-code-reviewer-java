package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import com.github.copilot.sdk.*;
import com.github.copilot.sdk.events.*;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
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
    
    public ReviewAgent(AgentConfig config,
                       CopilotClient client,
                       String githubToken,
                       GithubMcpConfig githubMcpConfig,
                       long timeoutMinutes) {
        this.config = config;
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.timeoutMinutes = timeoutMinutes;
    }
    
    /**
     * Executes the review for the given repository.
     * @param repository The repository to review (e.g., "owner/repo")
     * @return ReviewResult containing the review content
     */
    public CompletableFuture<ReviewResult> review(String repository) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeReview(repository);
            } catch (Exception e) {
                logger.error("Review failed for agent {}: {}", config.getName(), e.getMessage(), e);
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(repository)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }
    
    private ReviewResult executeReview(String repository) throws Exception {
        logger.info("Starting review with agent: {} for repository: {}", config.getName(), repository);
        
        // Configure GitHub MCP server for repository access
        Map<String, Object> githubMcp = githubMcpConfig.toMcpServer(githubToken);
        
        // Create session with agent configuration
        var sessionConfig = new SessionConfig()
            .setModel(config.getModel())
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(config.buildFullSystemPrompt()))
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
}
