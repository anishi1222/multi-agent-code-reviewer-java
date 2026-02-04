package dev.logicojp.reviewer.report;

import com.github.copilot.sdk.*;
import com.github.copilot.sdk.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generates executive summary by aggregating all agent review results.
 */
public class SummaryGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final long TIMEOUT_MINUTES = 5;
    
    private final Path outputDirectory;
    private final CopilotClient client;
    private final String summaryModel;
    
    public SummaryGenerator(Path outputDirectory, CopilotClient client) {
        this(outputDirectory, client, "claude-sonnet-4");
    }
    
    public SummaryGenerator(Path outputDirectory, CopilotClient client, String summaryModel) {
        this.outputDirectory = outputDirectory;
        this.client = client;
        this.summaryModel = summaryModel;
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
        ).get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        
        try {
            String prompt = buildSummaryPrompt(results, repository);
            var response = session.sendAndWait(prompt).get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return response.getData().getContent();
        } finally {
            session.close();
        }
    }
    
    private String buildSummarySystemPrompt() {
        return """
            あなたは経験豊富なテクニカルリードであり、コードレビュー結果を経営層向けにまとめる専門家です。
            
            以下の点を重視してエグゼクティブサマリーを作成してください：
            1. 技術的な問題を非技術者にも理解できるように説明
            2. ビジネスインパクトを明確に伝える
            3. 優先度に基づいたアクションプランを提示
            4. 全体的なリスク評価を行う
            
            出力は以下のフォーマットに従ってください：
            
            ## 総合評価
            {全体的なコード品質とリスクレベルの評価}
            
            ## 主要な発見事項
            {最も重要な発見事項を3-5点に絞って記載}
            
            ## 優先対応事項（Priority: Critical/High）
            
            | # | カテゴリ | タイトル | ビジネスインパクト |
            |---|----------|---------|-------------------|
            | 1 | {カテゴリ} | {タイトル} | {影響概要} |
            
            ## 各エージェント別サマリー
            
            ### {エージェント名}
            - 指摘件数: X件（Critical: X, High: X, Medium: X, Low: X）
            - 主要な発見: {概要}
            
            ## リスク評価
            {セキュリティ、品質、パフォーマンス等の観点からの総合リスク評価}
            
            ## 推奨アクションプラン
            1. **即時対応（24時間以内）**: {Critical/High優先度の対応}
            2. **短期対応（1週間以内）**: {Medium優先度の対応}
            3. **中期対応（1ヶ月以内）**: {Low優先度の対応}
            """;
    }
    
    private String buildSummaryPrompt(List<ReviewResult> results, String repository) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下は複数の専門エージェントによるGitHubリポジトリのコードレビュー結果です。\n");
        sb.append("これらを総合的に分析し、経営層向けのエグゼクティブサマリーを作成してください。\n\n");
        sb.append("**対象リポジトリ**: ").append(repository).append("\n\n");
        sb.append("---\n\n");
        
        for (ReviewResult result : results) {
            sb.append("## ").append(result.getAgentConfig().getDisplayName()).append("\n\n");
            
            if (result.isSuccess()) {
                sb.append(result.getContent());
            } else {
                sb.append("⚠️ レビュー失敗: ").append(result.getErrorMessage());
            }
            
            sb.append("\n\n---\n\n");
        }
        
        return sb.toString();
    }
    
    private String buildFinalReport(String summaryContent, String repository, 
                                     List<ReviewResult> results) {
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append("# エグゼクティブサマリー\n\n");
        sb.append("**日付**: ").append(LocalDate.now().format(DATE_FORMATTER)).append("  \n");
        sb.append("**対象リポジトリ**: ").append(repository).append("  \n");
        sb.append("**実施エージェント数**: ").append(results.size()).append("  \n");
        sb.append("**成功**: ").append(results.stream().filter(ReviewResult::isSuccess).count()).append("  \n");
        sb.append("**失敗**: ").append(results.stream().filter(r -> !r.isSuccess()).count()).append("\n\n");
        sb.append("---\n\n");
        
        // AI-generated summary content
        sb.append(summaryContent);
        
        // Individual report links
        sb.append("\n\n---\n\n");
        sb.append("## 個別レポート\n\n");
        for (ReviewResult result : results) {
            String filename = String.format("%s_%s.md", 
                result.getAgentConfig().getName(),
                LocalDate.now().format(FILE_DATE_FORMATTER));
            sb.append("- [").append(result.getAgentConfig().getDisplayName())
              .append("](").append(filename).append(")\n");
        }
        
        return sb.toString();
    }
    
    private void ensureOutputDirectory() throws IOException {
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
        }
    }
}
