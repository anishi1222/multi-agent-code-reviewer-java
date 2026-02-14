package dev.logicojp.reviewer.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CliParsing {
    public record OptionValue(String value, int newIndex) {
    }

    public record MultiValue(List<String> values, int newIndex) {
    }

    private CliParsing() {
    }

    public static boolean hasHelpFlag(String[] args) {
        String[] safeArgs = Objects.requireNonNullElse(args, new String[0]);
        for (String arg : safeArgs) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    public static OptionValue readSingleValue(String arg, String[] args, int index, String optionName) {
        String inline = inlineValue(arg);
        if (inline != null) {
            return new OptionValue(inline, index);
        }
        if (index + 1 >= args.length || args[index + 1].startsWith("-")) {
            throw new CliValidationException("Option requires a value: " + optionName, true);
        }
        return new OptionValue(args[index + 1], index + 1);
    }

    public static MultiValue readMultiValues(String arg, String[] args, int index, String optionName) {
        List<String> values = new ArrayList<>();
        String inline = inlineValue(arg);
        if (inline != null) {
            values.add(inline);
        }
        int newIndex = index;
        while (newIndex + 1 < args.length && !args[newIndex + 1].startsWith("-")) {
            values.add(args[++newIndex]);
        }
        if (values.isEmpty()) {
            throw new CliValidationException("Option requires at least one value: " + optionName, true);
        }
        return new MultiValue(values, newIndex);
    }

    public static List<String> splitComma(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parts;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private static String inlineValue(String arg) {
        int idx = arg.indexOf('=');
        if (idx > 0 && idx < arg.length() - 1) {
            return arg.substring(idx + 1);
        }
        return null;
    }

    /// Reads a token value, supporting stdin input via "-" to avoid
    /// exposing the token in process listings or shell history.
    /// The returned `char[]` from `readPassword` is zero-filled after conversion
    /// to minimize the window where the token exists in plain text.
    public static String readToken(String value) {
        if ("-".equals(value)) {
            try {
                if (System.console() != null) {
                    char[] chars = System.console().readPassword("GitHub Token: ");
                    if (chars == null) return "";
                    String token = new String(chars).trim();
                    Arrays.fill(chars, '\0');
                    return token;
                }
                return new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new CliValidationException("Failed to read token from stdin: " + e.getMessage(), false);
            }
        }
        return value;
    }
}

