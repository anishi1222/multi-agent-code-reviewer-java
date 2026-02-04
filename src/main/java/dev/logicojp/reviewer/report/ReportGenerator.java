package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates markdown report files for individual agent reviews.
 */
public class ReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    
    private final Path outputDirectory;
    
    public ReportGenerator(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
    
    /**
     * Generates a markdown report file for the given review result.
     * @param result The review result to generate a report for
     * @return Path to the generated report file
     */
    public Path generateReport(ReviewResult result) throws IOException {
        ensureOutputDirectory();
        
        AgentConfig config = result.getAgentConfig();
        String filename = String.format("%s_%s.md", 
            config.getName(), 
            LocalDate.now().format(FILE_DATE_FORMATTER));
        Path reportPath = outputDirectory.resolve(filename);
        
        String reportContent = buildReportContent(result);
        Files.writeString(reportPath, reportContent);
        
        logger.info("Generated report: {}", reportPath);
        return reportPath;
    }
    
    /**
     * Generates reports for all review results.
     * @param results List of review results
     * @return List of paths to generated report files
     */
    public List<Path> generateReports(List<ReviewResult> results) throws IOException {
        ensureOutputDirectory();
        
        return results.stream()
            .map(result -> {
                try {
                    return generateReport(result);
                } catch (IOException e) {
                    logger.error("Failed to generate report for {}: {}", 
                        result.getAgentConfig().getName(), e.getMessage());
                    return null;
                }
            })
            .filter(path -> path != null)
            .toList();
    }
    
    private String buildReportContent(ReviewResult result) {
        AgentConfig config = result.getAgentConfig();
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append("# ").append(config.getDisplayName()).append(" レビュー結果\n\n");
        sb.append("**日付**: ").append(LocalDate.now().format(DATE_FORMATTER)).append("  \n");
        sb.append("**対象リポジトリ**: ").append(result.getRepository()).append("\n\n");
        sb.append("---\n\n");
        
        // Review focus areas
        sb.append("## レビュー観点\n\n");
        for (String area : config.getFocusAreas()) {
            sb.append("- ").append(area).append("\n");
        }
        sb.append("\n---\n\n");
        
        // Review content or error
        sb.append("## 指摘事項\n\n");
        if (result.isSuccess()) {
            sb.append(result.getContent());
        } else {
            sb.append("⚠️ **レビュー失敗**\n\n");
            sb.append("エラー: ").append(result.getErrorMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    private void ensureOutputDirectory() throws IOException {
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
            logger.info("Created output directory: {}", outputDirectory);
        }
    }
}
