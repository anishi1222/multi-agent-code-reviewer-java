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
import java.util.Objects;

/// Generates markdown report files for individual agent reviews.
public class ReportGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    private final Path outputDirectory;
    private final ReportContentFormatter reportContentFormatter;
    
    public ReportGenerator(Path outputDirectory, TemplateService templateService) {
        this.outputDirectory = outputDirectory;
        this.reportContentFormatter = new ReportContentFormatter(templateService);
    }
    
    /// Generates a markdown report file for the given review result.
    /// @param result The review result to generate a report for
    /// @return Path to the generated report file
    public Path generateReport(ReviewResult result) throws IOException {
        ensureOutputDirectory();
        AgentConfig config = result.agentConfig();
        String date = LocalDate.now().format(DATE_FORMATTER);
        Path reportPath = createReportPath(config, date);
        
        String reportContent = reportContentFormatter.format(result, date);
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

    private Path createReportPath(AgentConfig config, String date) throws IOException {
        String filename = buildReportFilename(config, date);
        Path reportPath = outputDirectory.resolve(filename).normalize();
        if (!reportPath.startsWith(outputDirectory.normalize())) {
            throw new IOException("Invalid agent name: path traversal detected in '" + config.name() + "'");
        }
        return reportPath;
    }

    private String buildReportFilename(AgentConfig config, String date) {
        String safeName = sanitizeAgentName(config.name());
        return "%s_%s.md".formatted(safeName, date);
    }

    private String sanitizeAgentName(String agentName) {
        return agentName.replaceAll("[/\\\\]", "_");
    }
    
    private void ensureOutputDirectory() throws IOException {
        ReportFileUtils.ensureOutputDirectory(outputDirectory);
        logger.debug("Ensured output directory exists: {}", outputDirectory);
    }
}
