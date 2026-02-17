package dev.logicojp.reviewer.report;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ContentSanitizationRule(Pattern pattern, String replacement) {

    String apply(String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        matcher.reset();
        return matcher.replaceAll(replacement);
    }
}