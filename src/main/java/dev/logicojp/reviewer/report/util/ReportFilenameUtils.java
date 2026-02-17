package dev.logicojp.reviewer.report.util;

public final class ReportFilenameUtils {

    private ReportFilenameUtils() {
    }

    public static String sanitizeAgentName(String agentName) {
        return agentName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}