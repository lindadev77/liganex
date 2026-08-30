package tech.liganex.studio.module.rag.index;

import java.util.List;
import java.util.Set;

/** Backend-neutral index contract. Every operation has an explicit authenticated owner scope. */
public interface KnowledgeIndex {
    void upsert(List<IndexChunk> chunks);

    List<IndexHit> search(SearchQuery query);

    void delete(IndexScope scope);

    IndexHealth health();

    record IndexChunk(long ownerUserId, long knowledgeBaseId, long documentId, String chunkId,
                      String parentChunkId, int ordinal, String content, String parentContent,
                      String lexicalTerms, float[] embedding, int startOffset, int endOffset,
                      String sourceName) {
        public IndexChunk {
            if (ownerUserId <= 0 || knowledgeBaseId <= 0 || documentId <= 0) {
                throw new IllegalArgumentException("owner and resource scope are required");
            }
        }
    }

    record SearchQuery(long ownerUserId, Set<Long> knowledgeBaseIds, String query,
                       String lexicalTerms, float[] embedding, int candidateLimit, int finalLimit) {
        public SearchQuery {
            if (ownerUserId <= 0 || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
                throw new IllegalArgumentException("owner and knowledge base scope are required");
            }
            knowledgeBaseIds = Set.copyOf(knowledgeBaseIds);
        }
    }

    record IndexScope(long ownerUserId, Long knowledgeBaseId, Long documentId) {
        public IndexScope {
            if (ownerUserId <= 0 || knowledgeBaseId == null && documentId == null) {
                throw new IllegalArgumentException("owner and deletion scope are required");
            }
        }
    }

    record IndexHit(int rank, long knowledgeBaseId, long documentId, String chunkId,
                    String parentChunkId, String content, String parentContent, String sourceName,
                    Integer startOffset, Integer endOffset, Double backendScore) {
    }

    record IndexHealth(IndexBackend backend, boolean ready, int dimensions, String detail) {
    }
}
