package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;
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

/**
 * Generates markdown report files for individual agent reviews.
 */
public class ReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final Path outputDirectory;
    private final TemplateService templateService;
    
    public ReportGenerator(Path outputDirectory, TemplateService templateService) {
        this.outputDirectory = outputDirectory;
        this.templateService = templateService;
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
        
        // Build focus areas list
        StringBuilder focusAreasBuilder = new StringBuilder();
        for (String area : config.getFocusAreas()) {
            focusAreasBuilder.append("- ").append(area).append("\n");
        }
        
        // Build content section
        String content;
        if (result.isSuccess()) {
            content = result.getContent();
        } else {
            content = "⚠️ **レビュー失敗**\n\nエラー: " + result.getErrorMessage();
        }
        
        // Apply template
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("displayName", config.getDisplayName());
        placeholders.put("date", LocalDate.now().format(DATE_FORMATTER));
        placeholders.put("repository", result.getRepository());
        placeholders.put("focusAreas", focusAreasBuilder.toString());
        placeholders.put("content", content);
        
        return templateService.getReportTemplate(placeholders);
    }
    
    private void ensureOutputDirectory() throws IOException {
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
            logger.info("Created output directory: {}", outputDirectory);
        }
    }
}
