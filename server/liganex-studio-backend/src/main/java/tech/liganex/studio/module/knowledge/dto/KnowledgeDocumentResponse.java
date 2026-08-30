package tech.liganex.studio.module.knowledge.dto;

import tech.liganex.studio.module.knowledge.entity.KnowledgeDocument;

import java.time.Instant;

public record KnowledgeDocumentResponse(
        Long id,
        Long knowledgeBaseId,
        String title,
        String sourceType,
        String mediaType,
        String originalFilename,
        Long sizeBytes,
        String status,
        Integer progress,
        Integer chunkCount,
        String indexVersion,
        String errorSummary,
        Instant indexedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static KnowledgeDocumentResponse from(KnowledgeDocument document, String safeErrorSummary) {
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getTitle(),
                document.getSourceType(),
                document.getMediaType(),
                document.getOriginalFilename(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getProgress(),
                document.getChunkCount(),
                document.getIndexVersion(),
                safeErrorSummary,
                document.getIndexedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
