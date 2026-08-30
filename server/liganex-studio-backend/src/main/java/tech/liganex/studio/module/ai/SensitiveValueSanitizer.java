package tech.liganex.studio.module.ai;

import tech.liganex.studio.common.SensitiveDataSanitizer;

/** Prevents provider credentials from escaping through logs or client-facing errors. */
public final class SensitiveValueSanitizer {
    private SensitiveValueSanitizer() {
    }

    public static String sanitize(String value) {
        return SensitiveDataSanitizer.sanitize(value);
    }
}
