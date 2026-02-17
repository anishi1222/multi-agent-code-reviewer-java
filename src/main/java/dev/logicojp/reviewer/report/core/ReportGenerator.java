package dev.logicojp.reviewer.report.core;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.formatter.ReportContentFormatter;
import dev.logicojp.reviewer.report.util.ReportFileUtils;
import dev.logicojp.reviewer.report.util.ReportFilenameUtils;
import dev.logicojp.reviewer.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
     Path generateReport(ReviewResult result) throws IOException {
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

        List<Path> paths = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (ReviewResult result : results) {
            try {
                paths.add(generateReport(result));
            } catch (IOException e) {
                String agentName = result.agentConfig().name();
                logger.error("Failed to generate report for {}: {}", agentName, e.getMessage());
                failures.add(agentName);
            }
        }
        if (!failures.isEmpty()) {
            logger.warn("Failed to generate reports for {} agent(s): {}", failures.size(), failures);
        }
        return List.copyOf(paths);
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
        String safeName = ReportFilenameUtils.sanitizeAgentName(config.name());
        return "%s_%s.md".formatted(safeName, date);
    }
    
    private void ensureOutputDirectory() throws IOException {
        ReportFileUtils.ensureOutputDirectory(outputDirectory);
        logger.debug("Ensured output directory exists: {}", outputDirectory);
    }
}
