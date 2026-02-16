package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportGenerator")
class ReportGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("レビュー結果からMarkdownレポートを生成できる")
    void generateReportCreatesMarkdownFile() throws Exception {
        TemplateService templateService = new TemplateService(
            new TemplateConfig(null, null, null, null, null, null, null, null)
        );
        ReportGenerator generator = new ReportGenerator(tempDir, templateService);

        AgentConfig config = new AgentConfig(
            "security", "Security", "model", "system", "instruction", null, List.of("SQL"), List.of()
        );
        ReviewResult result = ReviewResult.builder()
            .agentConfig(config)
            .repository("owner/repo")
            .content("review body")
            .success(true)
            .timestamp(LocalDateTime.now())
            .build();

        Path output = generator.generateReport(result);

        assertThat(output).exists();
        String content = Files.readString(output);
        assertThat(content).contains("Security");
        assertThat(content).contains("review body");
    }
}
