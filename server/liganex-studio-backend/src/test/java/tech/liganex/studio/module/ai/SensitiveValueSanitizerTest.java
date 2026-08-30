package tech.liganex.studio.module.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueSanitizerTest {
    @Test
    void redactsBearerAndApiKeyShapes() {
        String sanitized = SensitiveValueSanitizer.sanitize(
                "Authorization: Bearer abc.def api-key=secret sk-or-v1-example");
        assertThat(sanitized).doesNotContain("abc.def", "secret", "sk-or-v1-example");
    }
}
