package dev.logicojp.reviewer.report;

import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generates executive summary by aggregating all agent review results.
 */
public class SummaryGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    private final long timeoutMinutes;
    private final Path systemPromptPath;
    private final Path userPromptPath;
    private final TemplateService templateService;
    
    public SummaryGenerator(
            Path outputDirectory, 
            CopilotClient client, 
            String summaryModel, 
            long timeoutMinutes,
            Path systemPromptPath,
            Path userPromptPath,
            TemplateService templateService) {
        this.outputDirectory = outputDirectory;
        this.client = client;
        this.summaryModel = summaryModel;
        this.timeoutMinutes = timeoutMinutes;
        this.systemPromptPath = systemPromptPath;
        this.userPromptPath = userPromptPath;
        this.templateService = templateService;
    }
    
    /**
     * Generates an executive summary from all review results.
     * @param results List of review results from all agents
     * @param repository The repository that was reviewed
     * @return Path to the generated summary file
     */
    public Path generateSummary(List<ReviewResult> results, String repository) throws Exception {
        ensureOutputDirectory();
        
        String filename = String.format("executive_summary_%s.md", 
            LocalDate.now().format(FILE_DATE_FORMATTER));
        Path summaryPath = outputDirectory.resolve(filename);
        
        logger.info("Generating executive summary from {} review results", results.size());
        
        // Build the summary using AI
        String summaryContent = buildSummaryWithAI(results, repository);
        
        // Build the final report
        String finalReport = buildFinalReport(summaryContent, repository, results);
        Files.writeString(summaryPath, finalReport);
        
        logger.info("Generated executive summary: {}", summaryPath);
        return summaryPath;
    }
    
    private String buildSummaryWithAI(List<ReviewResult> results, String repository) throws Exception {
        // Create a new session for summary generation
        logger.info("Using model for summary: {}", summaryModel);
        var session = client.createSession(
            new SessionConfig()
                .setModel(summaryModel)
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.REPLACE)
                    .setContent(buildSummarySystemPrompt()))
        ).get(timeoutMinutes, TimeUnit.MINUTES);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);
        
        try {
            String prompt = buildSummaryPrompt(results, repository);
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            return response.getData().getContent();
        } catch (java.util.concurrent.TimeoutException ex) {
            logger.error("Summary generation timed out: {}", ex.getMessage());
            return buildFallbackSummary(results, repository);
        } finally {
            session.close();
        }
    }

    private String buildFallbackSummary(List<ReviewResult> results, String repository) {
        // Build table rows
        StringBuilder tableRowsBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            tableRowsBuilder.append("| ").append(result.getAgentConfig().getDisplayName())
              .append(" | - | - | - | - | - |\n");
        }
        
        // Build agent summaries
        StringBuilder agentSummariesBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            agentSummariesBuilder.append("### ").append(result.getAgentConfig().getDisplayName()).append("\n");
            if (result.isSuccess()) {
                agentSummariesBuilder.append("- 指摘件数: 不明（タイムアウトのため集計不可）\n");
            } else {
                agentSummariesBuilder.append("- レビュー失敗: ").append(result.getErrorMessage()).append("\n");
            }
            agentSummariesBuilder.append("\n");
        }
        
        // Apply template
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tableRows", tableRowsBuilder.toString());
        placeholders.put("agentSummaries", agentSummariesBuilder.toString());
        
        return templateService.getFallbackSummaryTemplate(placeholders);
    }
    
    private String buildSummarySystemPrompt() throws IOException {
        if (systemPromptPath != null && Files.exists(systemPromptPath)) {
            logger.info("Loading system prompt from: {}", systemPromptPath);
            return Files.readString(systemPromptPath);
        }
        logger.warn("System prompt file not found at {}, using default", systemPromptPath);
        return getDefaultSystemPrompt();
    }
    
    private String getDefaultSystemPrompt() {
        return """
            あなたは経験豊富なテクニカルリードであり、コードレビュー結果を経営層向けにまとめる専門家です。
            
            以下の点を重視してエグゼクティブサマリーを作成してください：
            1. 技術的な問題を非技術者にも理解できるように説明
            2. ビジネスインパクトを明確に伝える
            3. 優先度に基づいたアクションプランを提示
            4. 全体的なリスク評価を行う
            5. 良い点と改善点を明確に区別する
            """;
    }
    
    private String buildSummaryPrompt(List<ReviewResult> results, String repository) throws IOException {
        String template = loadUserPromptTemplate();
        
        // Build results section
        StringBuilder resultsSection = new StringBuilder();
        for (ReviewResult result : results) {
            resultsSection.append("## ").append(result.getAgentConfig().getDisplayName()).append("\n\n");
            
            if (result.isSuccess()) {
                resultsSection.append(result.getContent());
            } else {
                resultsSection.append("⚠️ レビュー失敗: ").append(result.getErrorMessage());
            }
            
            resultsSection.append("\n\n---\n\n");
        }
        
        // Replace placeholders in template
        return template
            .replace("{{repository}}", repository)
            .replace("{{results}}", resultsSection.toString());
    }
    
    private String loadUserPromptTemplate() throws IOException {
        if (userPromptPath != null && Files.exists(userPromptPath)) {
            logger.info("Loading user prompt template from: {}", userPromptPath);
            String template = Files.readString(userPromptPath);
            // Remove Mustache-style conditionals since we're using simple replacement
            return cleanMustacheTemplate(template);
        }
        logger.warn("User prompt file not found, using default");
        return getDefaultUserPromptTemplate();
    }
    
    private String cleanMustacheTemplate(String template) {
        // Remove Mustache notation, keep just the placeholder format
        return template
            .replaceAll("\\{\\{#results\\}\\}", "")
            .replaceAll("\\{\\{/results\\}\\}", "")
            .replaceAll("\\{\\{#success\\}\\}", "")
            .replaceAll("\\{\\{/success\\}\\}", "")
            .replaceAll("\\{\\{\\^success\\}\\}", "")
            .replaceAll("\\{\\{displayName\\}\\}", "")
            .replaceAll("\\{\\{content\\}\\}", "")
            .replaceAll("\\{\\{errorMessage\\}\\}", "");
    }
    
    private String getDefaultUserPromptTemplate() {
        return """
            以下は複数の専門エージェントによるGitHubリポジトリのコードレビュー結果です。
            これらを総合的に分析し、経営層向けのエグゼクティブサマリーを作成してください。
            
            **対象リポジトリ**: {{repository}}
            
            ---
            
            {{results}}
            """;
    }
    
    private String buildFinalReport(String summaryContent, String repository, 
                                     List<ReviewResult> results) {
        // Build individual report links
        StringBuilder reportLinksBuilder = new StringBuilder();
        for (ReviewResult result : results) {
            String filename = String.format("%s_%s.md", 
                result.getAgentConfig().getName(),
                LocalDate.now().format(FILE_DATE_FORMATTER));
            reportLinksBuilder.append("- [").append(result.getAgentConfig().getDisplayName())
              .append("](").append(filename).append(")\n");
        }
        
        // Apply template
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("date", LocalDate.now().format(DATE_FORMATTER));
        placeholders.put("repository", repository);
        placeholders.put("agentCount", String.valueOf(results.size()));
        placeholders.put("successCount", String.valueOf(results.stream().filter(ReviewResult::isSuccess).count()));
        placeholders.put("failureCount", String.valueOf(results.stream().filter(r -> !r.isSuccess()).count()));
        placeholders.put("summaryContent", summaryContent);
        placeholders.put("reportLinks", reportLinksBuilder.toString());
        
        return templateService.getExecutiveSummaryTemplate(placeholders);
    }
    
    private void ensureOutputDirectory() throws IOException {
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
        }
    }
}
