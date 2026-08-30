package tech.liganex.studio.module.ai;

import org.junit.jupiter.api.Test;
import tech.liganex.studio.module.ai.config.ChatProperties;
import tech.liganex.studio.module.ai.config.EmbeddingProperties;

import static org.assertj.core.api.Assertions.assertThat;

class AiReadinessHealthIndicatorTest {
    @Test
    void reportsDownWithoutRuntimeCredentials() {
        AiModelClient models = new AiModelClient(new EmbeddingProperties(), new ChatProperties());
        var health = new AiReadinessHealthIndicator(models).health();
        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("embeddingConfigured", false)
                .containsEntry("chatConfigured", false);
    }
}
