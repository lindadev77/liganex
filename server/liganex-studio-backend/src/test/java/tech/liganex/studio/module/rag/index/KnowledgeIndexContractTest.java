package tech.liganex.studio.module.rag.index;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeIndexContractTest {
    @Test
    void searchCannotBeConstructedWithoutOwnerAndKnowledgeBaseScope() {
        assertThatThrownBy(() -> new KnowledgeIndex.SearchQuery(0, Set.of(1L), "q", "q", new float[3], 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeIndex.SearchQuery(1, Set.of(), "q", "q", new float[3], 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteCannotBeConstructedWithoutConcreteScope() {
        assertThatThrownBy(() -> new KnowledgeIndex.IndexScope(1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
