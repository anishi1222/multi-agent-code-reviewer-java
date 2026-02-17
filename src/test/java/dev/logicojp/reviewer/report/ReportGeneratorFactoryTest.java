package dev.logicojp.reviewer.report;

import dev.logicojp.reviewer.report.finding.AggregatedFinding;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;
import dev.logicojp.reviewer.report.finding.ReviewFindingSimilarity;
import dev.logicojp.reviewer.report.finding.FindingsExtractor;
import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.report.summary.SummaryGenerator;

import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.config.SummaryConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportGeneratorFactory")
class ReportGeneratorFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("createReportGeneratorはReportGeneratorインスタンスを返す")
    void createsReportGenerator() {
        var config = new TemplateConfig(tempDir.toString(),
            null, null, null, null, null, null, null);
        var templateService = new TemplateService(config);
        var factory = new ReportGeneratorFactory(templateService, new SummaryConfig(0, 0, 0));
        ReportGenerator generator = factory.createReportGenerator(Path.of("/tmp/reports"));
        assertThat(generator).isNotNull();
    }

    @Test
    @DisplayName("コンストラクタ注入したReportGeneratorCreatorが利用される")
    void usesInjectedReportGeneratorCreator() {
        var config = new TemplateConfig(tempDir.toString(),
            null, null, null, null, null, null, null);
        var templateService = new TemplateService(config);

        var reportCreatorCalled = new AtomicBoolean(false);
        var summaryCreatorCalled = new AtomicBoolean(false);

        var factory = new ReportGeneratorFactory(
            templateService,
            new SummaryConfig(0, 0, 0),
            (outputDirectory, ts) -> {
                reportCreatorCalled.set(true);
                return new ReportGenerator(outputDirectory, ts);
            },
            (outputDirectory, client, summaryModel, reasoningEffort, timeoutMinutes, ts, summaryConfig) -> {
                summaryCreatorCalled.set(true);
                return new SummaryGenerator(
                    outputDirectory,
                    client,
                    summaryModel,
                    reasoningEffort,
                    timeoutMinutes,
                    ts,
                    summaryConfig
                );
            }
        );

        factory.createReportGenerator(Path.of("/tmp/reports"));

        assertThat(reportCreatorCalled).isTrue();
        assertThat(summaryCreatorCalled).isFalse();
    }

    @Test
    @DisplayName("コンストラクタ注入したSummaryGeneratorCreatorが利用される")
    void usesInjectedSummaryGeneratorCreator() {
        var config = new TemplateConfig(tempDir.toString(),
            null, null, null, null, null, null, null);
        var templateService = new TemplateService(config);

        var reportCreatorCalled = new AtomicBoolean(false);
        var summaryCreatorCalled = new AtomicBoolean(false);

        var factory = new ReportGeneratorFactory(
            templateService,
            new SummaryConfig(0, 0, 0),
            (outputDirectory, ts) -> {
                reportCreatorCalled.set(true);
                return new ReportGenerator(outputDirectory, ts);
            },
            (outputDirectory, client, summaryModel, reasoningEffort, timeoutMinutes, ts, summaryConfig) -> {
                summaryCreatorCalled.set(true);
                return new SummaryGenerator(
                    outputDirectory,
                    client,
                    summaryModel,
                    reasoningEffort,
                    timeoutMinutes,
                    ts,
                    summaryConfig
                );
            }
        );

        CopilotClient client = null;
        factory.createSummaryGenerator(Path.of("/tmp/reports"), client, "model", null, 3L);

        assertThat(summaryCreatorCalled).isTrue();
        assertThat(reportCreatorCalled).isFalse();
    }
}
