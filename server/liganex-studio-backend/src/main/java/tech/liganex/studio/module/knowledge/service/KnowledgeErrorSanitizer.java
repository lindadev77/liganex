package tech.liganex.studio.module.knowledge.service;

import java.util.regex.Pattern;

final class KnowledgeErrorSanitizer {

    private static final int MAX_LENGTH = 500;
    private static final Pattern SECRET_TOKEN = Pattern.compile("(?i)\\bsk-[a-z0-9_-]{8,}\\b");
    private static final Pattern CREDENTIAL_FIELD = Pattern.compile(
            "(?i)(authorization|api[-_ ]?key|access[-_ ]?token)\\s*[:=]\\s*[^\\s,;]+"
    );

    private KnowledgeErrorSanitizer() {
    }

    static String sanitize(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String sanitized = SECRET_TOKEN.matcher(summary).replaceAll("[redacted]");
        sanitized = CREDENTIAL_FIELD.matcher(sanitized).replaceAll("$1=[redacted]");
        return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
    }
}
