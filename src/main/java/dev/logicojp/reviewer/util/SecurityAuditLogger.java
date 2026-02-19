package dev.logicojp.reviewer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/// Security audit logger helper.
/// Writes audit events to dedicated SECURITY_AUDIT logger with MDC fields.
public final class SecurityAuditLogger {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("SECURITY_AUDIT");

    private SecurityAuditLogger() {
    }

    public static void log(String eventCategory, String eventAction, String message) {
        log(eventCategory, eventAction, message, Map.of());
    }

    public static void log(String eventCategory,
                           String eventAction,
                           String message,
                           Map<String, String> attributes) {
        MDC.put("event.category", safe(eventCategory));
        MDC.put("event.action", safe(eventAction));
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    MDC.put("audit." + key, safe(value));
                }
            });
        }
        try {
            AUDIT_LOGGER.info(message);
        } finally {
            MDC.clear();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
