package dev.logicojp.reviewer.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/// Stateless CLI argument parsing utilities.
///
/// **Visibility policy**: {@code hasHelpFlag} is {@code public} — it is the public API.
/// All other parsing methods are package-private (used only within the {@code cli} package).
public final class CliParsing {
    private static final int MAX_STDIN_TOKEN_BYTES = 256;
    static final String STDIN_TOKEN_SENTINEL = "-";

    public record OptionValue(String value, int newIndex) {
    }

    public record MultiValue(List<String> values, int newIndex) {
    }

    private CliParsing() {
    }

    public static boolean hasHelpFlag(String[] args) {
        if (args == null) {
            return false;
        }
        return Arrays.stream(args).anyMatch(CliParsing::isHelpOption);
    }

     static OptionValue readSingleValue(String arg, String[] args, int index, String optionName) {
        String inline = inlineValue(arg);
        if (inline != null) {
            return new OptionValue(inline, index);
        }
        if (isMissingOptionValue(args, index)) {
            throw missingOptionValue(optionName);
        }
        return new OptionValue(args[index + 1], index + 1);
    }

     static MultiValue readMultiValues(String arg, String[] args, int index, String optionName) {
        List<String> values = new ArrayList<>();
        addInlineValue(arg, values);
        int newIndex = index;
        while (hasNextNonOptionArg(args, newIndex)) {
            values.add(args[++newIndex]);
        }
        if (values.isEmpty()) {
            throw new CliValidationException("Option requires at least one value: " + optionName, true);
        }
        return new MultiValue(values, newIndex);
    }

     static int readInto(String[] args, int i, String optName, Consumer<String> setter) {
        OptionValue value = readSingleValue(args[i], args, i, optName);
        setter.accept(value.value());
        return value.newIndex();
    }

     static int readMultiInto(String[] args, int i, String optName, Consumer<String> setter) {
        MultiValue values = readMultiValues(args[i], args, i, optName);
        for (String value : values.values()) {
            setter.accept(value);
        }
        return values.newIndex();
    }

     static int readTokenInto(String[] args, int i, String optName, Consumer<String> setter) {
        return readInto(args, i, optName, value -> setter.accept(readTokenWithWarning(value)));
    }

     static List<String> splitComma(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return List.of();
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return List.copyOf(parts);
    }

    private static String inlineValue(String arg) {
        int idx = arg.indexOf('=');
        if (idx > 0 && idx < arg.length() - 1) {
            return arg.substring(idx + 1);
        }
        return null;
    }

    private static boolean isHelpOption(String arg) {
        return "-h".equals(arg) || "--help".equals(arg);
    }

    private static void addInlineValue(String arg, List<String> values) {
        String inline = inlineValue(arg);
        if (inline != null) {
            values.add(inline);
        }
    }

    private static boolean hasNextNonOptionArg(String[] args, int index) {
        return index + 1 < args.length && !args[index + 1].startsWith("-");
    }

    /// Reads a token value with a security check.
    /// Rejects direct token values passed via command line to prevent
    /// exposure in process listings and shell history.
    /// Only `"-"` (stdin sentinel) is accepted; for other values a
    /// {@link CliValidationException} is thrown.
     static String readTokenWithWarning(String value) {
        if (!STDIN_TOKEN_SENTINEL.equals(value)) {
            throw directTokenPassingNotSupported();
        }
        return STDIN_TOKEN_SENTINEL;
    }

    /// Reads a token value, supporting stdin input via "-" to avoid
    /// exposing the token in process listings or shell history.
    /// The returned `char[]` from `readPassword` is zero-filled after conversion
    /// to minimize the window where the token exists in plain text.
    ///
    /// **Security note**: The returned `String` is immutable and cannot be zeroed.
    /// It will remain in the JVM heap until garbage collected. Callers should
    /// minimize the scope of token references and prefer short-lived tokens
    /// (e.g., fine-grained personal access tokens) to reduce exposure window.
     static String readToken(String value) {
        if (STDIN_TOKEN_SENTINEL.equals(value)) {
            try {
                if (System.console() != null) {
                    char[] chars = System.console().readPassword("GitHub Token: ");
                    if (chars == null) return "";
                    // NOTE: SDK APIs require String; char[] is cleared immediately after conversion.
                    String token = String.valueOf(chars).trim();
                    Arrays.fill(chars, '\0');
                    return token;
                }
                byte[] raw = System.in.readNBytes(MAX_STDIN_TOKEN_BYTES);
                String token = new String(raw, StandardCharsets.UTF_8).trim();
                Arrays.fill(raw, (byte) 0);
                return token;
            } catch (IOException e) {
                throw tokenReadFailure(e);
            }
        }
        return value;
    }

    private static boolean isMissingOptionValue(String[] args, int index) {
        return index + 1 >= args.length || args[index + 1].startsWith("-");
    }

    private static CliValidationException missingOptionValue(String optionName) {
        return new CliValidationException("Option requires a value: " + optionName, true);
    }

    private static CliValidationException directTokenPassingNotSupported() {
        return new CliValidationException(
            "Direct token passing via command line is not supported for security reasons. "
                + "Use '--token -' (stdin) or set GITHUB_TOKEN environment variable.", true);
    }

    private static CliValidationException tokenReadFailure(IOException e) {
        return new CliValidationException("Failed to read token from stdin: " + e.getMessage(), false);
    }
}

