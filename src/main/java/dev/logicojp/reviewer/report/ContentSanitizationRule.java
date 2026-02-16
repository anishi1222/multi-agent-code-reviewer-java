package dev.logicojp.reviewer.report;

import java.util.regex.Pattern;

record ContentSanitizationRule(Pattern pattern, String replacement) {

    String apply(String input) {
        return pattern.matcher(input).replaceAll(replacement);
    }
}