package tech.liganex.studio.module.rag.index;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ragIndex")
@RequiredArgsConstructor
public class RagIndexHealthIndicator implements HealthIndicator {
    private final KnowledgeIndex index;

    @Override
    public Health health() {
        KnowledgeIndex.IndexHealth status = index.health();
        Health.Builder builder = status.ready() ? Health.up() : Health.down();
        return builder.withDetail("backend", status.backend())
                .withDetail("dimensions", status.dimensions())
                .withDetail("detail", status.detail())
                .build();
    }
}
