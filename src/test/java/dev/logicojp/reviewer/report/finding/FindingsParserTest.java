package dev.logicojp.reviewer.report.finding;

import dev.logicojp.reviewer.report.core.ReviewResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FindingsParser")
class FindingsParserTest {

    @Test
    @DisplayName("見出しとPriorityを対応付けて抽出する")
    void extractsFindingsFromHeadingsAndPriorities() {
        String content = """
            ### [1]. 指摘A
            | **Priority** | High |

            ### [2]. 指摘B
            | Priority | Low |
            """;

        List<FindingsExtractor.Finding> findings = FindingsParser.extractFindings(content, "agent");

        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).title()).isEqualTo("指摘A");
        assertThat(findings.get(0).priority()).isEqualTo("High");
        assertThat(findings.get(1).title()).isEqualTo("指摘B");
        assertThat(findings.get(1).priority()).isEqualTo("Low");
    }

    @Test
    @DisplayName("Priorityのみの場合はFinding 1形式で補完する")
    void fillsTitleWhenOnlyPriorityExists() {
        String content = "| Priority | Medium |";

        List<FindingsExtractor.Finding> findings = FindingsParser.extractFindings(content, "agent");

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().title()).isEqualTo("Finding 1");
        assertThat(findings.getFirst().priority()).isEqualTo("Medium");
    }

    @Test
    @DisplayName("見出しのみの場合はUnknown Priorityで補完する")
    void fillsPriorityWhenOnlyHeadingExists() {
        String content = "### 1. 指摘のみ";

        List<FindingsExtractor.Finding> findings = FindingsParser.extractFindings(content, "agent");

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().title()).isEqualTo("指摘のみ");
        assertThat(findings.getFirst().priority()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("指摘事項なしを含む場合は空を返す")
    void returnsEmptyWhenNoFindingsMarkerExists() {
        List<FindingsExtractor.Finding> findings = FindingsParser.extractFindings("指摘事項なし", "agent");
        assertThat(findings).isEmpty();
    }
}
