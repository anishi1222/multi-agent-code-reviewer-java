package dev.logicojp.reviewer.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

@Singleton
public class CopilotTimeoutResolver {

    private static final Logger logger = LoggerFactory.getLogger(CopilotTimeoutResolver.class);

    public long resolveEnvTimeout(String envVar, long defaultValue) {
        return resolveEnvTimeout(envVar, defaultValue, System::getenv);
    }

    long resolveEnvTimeout(String envVar, long defaultValue, Function<String, String> envReader) {
        String value = envReader.apply(envVar);
        if (isBlank(value)) {
            return defaultValue;
        }
        return parseOrDefault(envVar, value, defaultValue);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long parseOrDefault(String envVar, String value, long defaultValue) {
        try {
            long parsed = Long.parseLong(value.trim());
            return nonNegativeOrDefault(parsed, defaultValue);
        } catch (NumberFormatException _) {
            logInvalidValue(envVar, value);
            return defaultValue;
        }
    }

    private long nonNegativeOrDefault(long value, long defaultValue) {
        return value >= 0 ? value : defaultValue;
    }

    private void logInvalidValue(String envVar, String value) {
        logger.warn("Invalid {} value: {}. Using default.", envVar, value);
    }
}
