package tech.liganex.studio.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tech.liganex.studio.module.chat.entity.ChatConversation;
import tech.liganex.studio.module.chat.entity.ChatMessage;

import java.time.Instant;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record CreateConversationRequest(
            @Size(max = 128) String title,
            @NotEmpty List<Long> knowledgeBaseIds) {
    }

    public record UpdateConversationRequest(@NotBlank @Size(max = 128) String title) {
    }

    public record AskRequest(@NotBlank @Size(max = 8000) String question) {
    }

    public record ConversationResponse(Long id, String title, String status,
                                       List<Long> knowledgeBaseIds, Instant createdAt, Instant updatedAt) {
        public static ConversationResponse from(ChatConversation value, List<Long> ids) {
            return new ConversationResponse(value.getId(), value.getTitle(), value.getStatus(), ids,
                    value.getCreatedAt(), value.getUpdatedAt());
        }
    }

    public record MessageResponse(Long id, long sequence, String role, String content, String status,
                                  String citations, Instant createdAt, Instant completedAt) {
        public static MessageResponse from(ChatMessage value) {
            return new MessageResponse(value.getId(), value.getSequence(), value.getRole(), value.getContent(),
                    value.getStatus(), value.getCitations(), value.getCreatedAt(), value.getCompletedAt());
        }
    }

    public record Citation(long documentId, String chunkId, String sourceName, String excerpt,
                           Integer startOffset, Integer endOffset, boolean available) {
    }

    public record TokenEvent(String token) {
    }

    public record DoneEvent(Long messageId, String answer, List<Citation> citations) {
    }

    public record ErrorEvent(String code, String message) {
    }
}
