package tech.liganex.studio.common;

import java.util.regex.Pattern;

/** Redacts credentials before text is returned to clients or written to logs. */
public final class SensitiveDataSanitizer {
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern OPEN_ROUTER_KEY = Pattern.compile("sk-or-v1-[A-Za-z0-9_-]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)(api[-_ ]?key[=: ]+)[^,;\\s]+");

    private SensitiveDataSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return API_KEY.matcher(OPEN_ROUTER_KEY.matcher(BEARER.matcher(value)
                        .replaceAll("Bearer [REDACTED]"))
                .replaceAll("[REDACTED]"))
                .replaceAll("$1[REDACTED]");
    }
}
