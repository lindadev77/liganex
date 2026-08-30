package tech.liganex.studio.module.rag.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tech.liganex.studio.module.rag.index.IndexBackend;

@Component
@RequiredArgsConstructor
public class IndexBackendValidator {
    private final RagProperties properties;

    @PostConstruct
    void validate() {
        if (properties.getIndex().getBackend() != IndexBackend.PGVECTOR) {
            throw new IllegalStateException("Configured RAG index backend is not implemented: "
                    + properties.getIndex().getBackend());
        }
    }
}
