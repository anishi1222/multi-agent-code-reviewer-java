package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.report.ReportGenerator;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.report.SummaryGenerator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for generating review reports and executive summaries.
 */
@Singleton
public class ReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    
    private final CopilotService copilotService;
    private final ExecutionConfig executionConfig;
    private final TemplateConfig templateConfig;
    private final TemplateService templateService;
    
    @Inject
    public ReportService(
            CopilotService copilotService, 
            ExecutionConfig executionConfig,
            TemplateConfig templateConfig,
            TemplateService templateService) {
        this.copilotService = copilotService;
        this.executionConfig = executionConfig;
        this.templateConfig = templateConfig;
        this.templateService = templateService;
    }
    
    /**
     * Generates individual reports for each review result.
     * @param results List of review results
     * @param outputDirectory Directory to write reports to
     * @return List of paths to generated report files
     */
    public List<Path> generateReports(List<ReviewResult> results, Path outputDirectory) 
            throws IOException {
        logger.info("Generating {} individual reports", results.size());
        
        ReportGenerator generator = new ReportGenerator(outputDirectory, templateService);
        return generator.generateReports(results);
    }
    
    /**
     * Generates an executive summary from all review results.
     * @param results List of review results
     * @param repository Target repository that was reviewed
     * @param outputDirectory Directory to write summary to
     * @param summaryModel LLM model to use for summary generation
     * @return Path to the generated summary file
     */
    public Path generateSummary(
            List<ReviewResult> results,
            String repository,
            Path outputDirectory,
            String summaryModel) throws Exception {
        
        logger.info("Generating executive summary using model: {}", summaryModel);
        
        Path templateDir = Path.of(templateConfig.directory());
        Path systemPromptPath = templateDir.resolve(templateConfig.summarySystemPrompt());
        Path userPromptPath = templateDir.resolve(templateConfig.summaryUserPrompt());
        
        SummaryGenerator generator = new SummaryGenerator(
            outputDirectory, 
            copilotService.getClient(), 
            summaryModel, 
            executionConfig.summaryTimeoutMinutes(),
            systemPromptPath,
            userPromptPath,
            templateService);
        
        return generator.generateSummary(results, repository);
    }
}
