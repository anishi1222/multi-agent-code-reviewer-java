package dev.logicojp.reviewer.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FindingsSummaryFormatter")
class FindingsSummaryFormatterTest {

    @Test
    @DisplayName("優先度順にグループ化してMarkdownを生成する")
    void formatsFindingsInSeverityOrder() {
        var findings = List.of(
            new FindingsExtractor.Finding("Low issue", "Low", "quality"),
            new FindingsExtractor.Finding("Critical issue", "Critical", "security"),
            new FindingsExtractor.Finding("High issue", "High", "security")
        );

        String summary = FindingsSummaryFormatter.formatSummary(findings);

        assertThat(summary).contains("#### Critical (1)");
        assertThat(summary).contains("#### High (1)");
        assertThat(summary).contains("#### Low (1)");
        assertThat(summary.indexOf("#### Critical")).isLessThan(summary.indexOf("#### High"));
        assertThat(summary.indexOf("#### High")).isLessThan(summary.indexOf("#### Low"));
    }
}