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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/// Generates markdown report files for individual agent reviews.
public class ReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    private final Path outputDirectory;
    private final TemplateService templateService;
    
    public ReportGenerator(Path outputDirectory, TemplateService templateService) {
        this.outputDirectory = outputDirectory;
        this.templateService = templateService;
    }
    
    /// Generates a markdown report file for the given review result.
    /// @param result The review result to generate a report for
    /// @return Path to the generated report file
    public Path generateReport(ReviewResult result) throws IOException {
        ensureOutputDirectory();
        
        AgentConfig config = result.agentConfig();
        // Sanitize agent name to prevent path traversal via malicious .agent.md definitions
        String safeName = config.name().replaceAll("[/\\\\]", "_");
        String filename = "%s_%s.md".formatted(
            safeName,
            LocalDate.now().format(DATE_FORMATTER));
        Path reportPath = outputDirectory.resolve(filename).normalize();
        if (!reportPath.startsWith(outputDirectory.normalize())) {
            throw new IOException("Invalid agent name: path traversal detected in '" + config.name() + "'");
        }
        
        String reportContent = buildReportContent(result);
        Files.writeString(reportPath, reportContent);
        
        logger.info("Generated report: {}", reportPath);
        return reportPath;
    }
    
    /// Generates reports for all review results.
    /// @param results List of review results
    /// @return List of paths to generated report files
    public List<Path> generateReports(List<ReviewResult> results) throws IOException {
        ensureOutputDirectory();
        
        return results.stream()
            .map(result -> {
                try {
                    return generateReport(result);
                } catch (IOException e) {
                    logger.error("Failed to generate report for {}: {}", 
                        result.agentConfig().name(), e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }
    
    private String buildReportContent(ReviewResult result) {
        AgentConfig config = result.agentConfig();
        
        // Build focus areas list
        var focusAreasJoiner = new StringJoiner("\n", "", "\n");
        for (String area : config.focusAreas()) {
            focusAreasJoiner.add("- " + area);
        }
        
        // Build content section
        String content = result.isSuccess()
            ? result.content()
            : "⚠️ **レビュー失敗**\n\nエラー: " + result.errorMessage();
        
        // Apply template
        var placeholders = Map.of(
            "displayName", config.displayName(),
            "date", LocalDate.now().format(DATE_FORMATTER),
            "repository", result.repository(),
            "focusAreas", focusAreasJoiner.toString(),
            "content", content);
        
        return templateService.getReportTemplate(placeholders);
    }
    
    private void ensureOutputDirectory() throws IOException {
        ReportFileUtils.ensureOutputDirectory(outputDirectory);
        logger.debug("Ensured output directory exists: {}", outputDirectory);
    }
}
