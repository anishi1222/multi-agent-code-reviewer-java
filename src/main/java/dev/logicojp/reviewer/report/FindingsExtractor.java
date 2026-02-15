package dev.logicojp.reviewer.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Extracts structured findings from review result content.
///
/// Parses the Markdown output format produced by review agents and builds
/// a deterministic summary of all findings grouped by priority level.
/// This summary is included in the executive summary report without
/// requiring an additional LLM call.
public final class FindingsExtractor {

    private static final Logger logger = LoggerFactory.getLogger(FindingsExtractor.class);

    private FindingsExtractor() {
        // Utility class — not instantiable
    }

    /// Pattern to match priority values in review output tables.
    /// Matches lines like: `| **Priority** | Critical |` or `| **Priority** | High |`
    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
        "\\|\\s*\\*{0,2}Priority\\*{0,2}\\s*\\|\\s*(Critical|High|Medium|Low)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    /// Pattern to match finding headings like: `### 1. タイトル` or `### [1] タイトル`
    private static final Pattern FINDING_HEADING_PATTERN = Pattern.compile(
        "^###\\s+\\[?\\d+\\]?\\.?\\s+(.+)$",
        Pattern.MULTILINE
    );

    /// A single extracted finding.
    /// @param title   The finding title
    /// @param priority The priority level (Critical, High, Medium, Low)
    /// @param agent   The agent name that produced the finding
    record Finding(String title, String priority, String agent) {}

    /// Builds a deterministic findings summary from all review results.
    ///
    /// Extracts findings from each successful agent's review content
    /// and groups them by priority level. The output is a Markdown-formatted
    /// summary suitable for inclusion in the executive summary.
    ///
    /// @param results List of review results from all agents
    /// @return Formatted findings summary, or empty string if no findings
    public static String buildFindingsSummary(List<ReviewResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        List<Finding> allFindings = new ArrayList<>();

        for (ReviewResult result : results) {
            if (!result.isSuccess() || result.content() == null || result.content().isBlank()) {
                continue;
            }

            String agentName = result.agentConfig() != null
                ? result.agentConfig().displayName()
                : "unknown";

            List<Finding> findings = extractFindings(result.content(), agentName);
            allFindings.addAll(findings);
        }

        if (allFindings.isEmpty()) {
            return "";
        }

        return formatSummary(allFindings);
    }

    /// Extracts findings from a single agent's review content.
    static List<Finding> extractFindings(String content, String agentName) {
        List<Finding> findings = new ArrayList<>();

        List<String> titles = new ArrayList<>();
        List<String> priorities = new ArrayList<>();

        // Single pass over lines to reduce full-content regex scans.
        for (String line : content.split("\\R")) {
            Matcher headingMatcher = FINDING_HEADING_PATTERN.matcher(line);
            if (headingMatcher.find()) {
                titles.add(headingMatcher.group(1).trim());
                continue;
            }

            Matcher priorityMatcher = PRIORITY_PATTERN.matcher(line);
            if (priorityMatcher.find()) {
                priorities.add(capitalize(priorityMatcher.group(1).trim()));
            }
        }

        // Check for "指摘事項なし" (no findings)
        if (titles.isEmpty() && content.contains("指摘事項なし")) {
            return List.of();
        }

        // Pair titles with priorities; fall back if counts don't match
        int count = Math.min(titles.size(), priorities.size());
        for (int i = 0; i < count; i++) {
            findings.add(new Finding(titles.get(i), priorities.get(i), agentName));
        }

        // If we found priorities but no headings, create generic entries
        if (titles.isEmpty() && !priorities.isEmpty()) {
            for (int i = 0; i < priorities.size(); i++) {
                findings.add(new Finding("Finding " + (i + 1), priorities.get(i), agentName));
            }
        }

        // If we found headings but no priorities, record with unknown priority
        if (!titles.isEmpty() && priorities.isEmpty()) {
            for (String title : titles) {
                findings.add(new Finding(title, "Unknown", agentName));
            }
        }

        logger.debug("Agent '{}': extracted {} finding(s)", agentName, findings.size());
        return findings;
    }

    /// Formats findings into a Markdown summary grouped by priority.
    private static String formatSummary(List<Finding> findings) {
        var sb = new StringBuilder();

        // Group findings by priority in a single pass
        Map<String, List<Finding>> grouped = new LinkedHashMap<>();
        for (Finding f : findings) {
            grouped.computeIfAbsent(f.priority(), _ -> new ArrayList<>()).add(f);
        }

        // Output in severity order
        for (String priority : List.of("Critical", "High", "Medium", "Low", "Unknown")) {
            List<Finding> group = grouped.getOrDefault(priority, List.of());

            if (group.isEmpty()) {
                continue;
            }

            sb.append("#### ").append(priority).append(" (").append(group.size()).append(")\n\n");
            for (Finding f : group) {
                sb.append("- **").append(f.title()).append("** — ").append(f.agent()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().stripTrailing();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
