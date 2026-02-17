package dev.logicojp.reviewer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ConfigDefaults {

    private static final Logger logger = LoggerFactory.getLogger(ConfigDefaults.class);

    private ConfigDefaults() {
    }

    static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    static int defaultIfNonPositive(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    static long defaultIfNonPositive(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    static <T> List<T> defaultListIfEmpty(List<T> values, List<T> defaultValues) {
        return values == null || values.isEmpty() ? defaultValues : List.copyOf(values);
    }

    static List<String> loadListFromResource(String resourcePath, List<String> fallback) {
        try (InputStream stream = ConfigDefaults.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                logger.debug("Default config resource not found: {}", resourcePath);
                return List.copyOf(fallback);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<String> values = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        values.add(trimmed);
                    }
                }

                if (values.isEmpty()) {
                    logger.debug("Default config resource is empty: {}", resourcePath);
                    return List.copyOf(fallback);
                }

                return List.copyOf(values);
            }
        } catch (IOException e) {
            logger.debug("Failed to read default config resource '{}': {}", resourcePath, e.getMessage());
            return List.copyOf(fallback);
        }
    }
}