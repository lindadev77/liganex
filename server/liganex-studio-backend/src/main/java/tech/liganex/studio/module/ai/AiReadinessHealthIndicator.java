package tech.liganex.studio.module.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ragModels")
@RequiredArgsConstructor
public class AiReadinessHealthIndicator implements HealthIndicator {
    private final AiModelClient models;

    @Override
    public Health health() {
        boolean embedding = models.embeddingReady();
        boolean chat = models.chatReady();
        Health.Builder builder = embedding && chat ? Health.up() : Health.down();
        return builder.withDetail("embeddingConfigured", embedding)
                .withDetail("chatConfigured", chat)
                .withDetail("embeddingDimensions", models.dimensions())
                .build();
    }
}
