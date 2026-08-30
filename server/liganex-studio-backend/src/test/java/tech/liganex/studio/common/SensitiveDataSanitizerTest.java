package tech.liganex.studio.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataSanitizerTest {
    @Test
    void redactsCommonCredentialShapes() {
        String sanitized = SensitiveDataSanitizer.sanitize(
                "Authorization: Bearer abc.def api_key=secret sk-or-v1-example");

        assertThat(sanitized).doesNotContain("abc.def", "secret", "sk-or-v1-example");
        assertThat(sanitized).contains("[REDACTED]");
    }
}
