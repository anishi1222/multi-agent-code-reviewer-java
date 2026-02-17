package dev.logicojp.reviewer.report.formatter;

import dev.logicojp.reviewer.report.finding.FindingsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FindingsSummaryFormatter {

    private FindingsSummaryFormatter() {
    }

    public static String formatSummary(List<FindingsExtractor.Finding> findings) {
        var sb = new StringBuilder();
        Map<String, List<FindingsExtractor.Finding>> grouped = groupByPriority(findings);

        for (String priority : List.of("Critical", "High", "Medium", "Low", "Unknown")) {
            List<FindingsExtractor.Finding> group = grouped.getOrDefault(priority, List.of());
            if (group.isEmpty()) {
                continue;
            }
            appendPriorityBlock(sb, priority, group);
        }

        return sb.toString().stripTrailing();
    }

    private static Map<String, List<FindingsExtractor.Finding>> groupByPriority(List<FindingsExtractor.Finding> findings) {
        Map<String, List<FindingsExtractor.Finding>> grouped = new LinkedHashMap<>();
        for (FindingsExtractor.Finding finding : findings) {
            grouped.computeIfAbsent(finding.priority(), _ -> new ArrayList<>()).add(finding);
        }
        return grouped;
    }

    private static void appendPriorityBlock(StringBuilder sb,
                                            String priority,
                                            List<FindingsExtractor.Finding> group) {
        sb.append("#### ").append(priority).append(" (").append(group.size()).append(")\n\n");
        for (FindingsExtractor.Finding finding : group) {
            sb.append("- **").append(finding.title()).append("** — ").append(finding.agent()).append("\n");
        }
        sb.append("\n");
    }
}