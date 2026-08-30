package tech.liganex.studio.module.rag.config;

import org.junit.jupiter.api.Test;
import tech.liganex.studio.module.rag.index.IndexBackend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexBackendValidatorTest {
    @Test
    void rejectsUnimplementedBackendInsteadOfFallingBack() {
        RagProperties properties = new RagProperties();
        properties.getIndex().setBackend(IndexBackend.QDRANT);
        assertThatThrownBy(() -> new IndexBackendValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QDRANT");
    }
}
