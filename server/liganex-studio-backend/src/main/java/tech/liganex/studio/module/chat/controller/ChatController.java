package tech.liganex.studio.module.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.chat.dto.ChatDtos.AskRequest;
import tech.liganex.studio.module.chat.dto.ChatDtos.ConversationResponse;
import tech.liganex.studio.module.chat.dto.ChatDtos.CreateConversationRequest;
import tech.liganex.studio.module.chat.dto.ChatDtos.MessageResponse;
import tech.liganex.studio.module.chat.dto.ChatDtos.UpdateConversationRequest;
import tech.liganex.studio.module.chat.service.ChatGenerationService;
import tech.liganex.studio.module.chat.service.ConversationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class ChatController {
    private final ConversationService conversations;
    private final ChatGenerationService generation;

    @PostMapping
    public ApiResponse<ConversationResponse> create(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.ok(conversations.create(userId, request.title(), request.knowledgeBaseIds()));
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(conversations.list(userId));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long conversationId) {
        return ApiResponse.ok(conversations.get(userId, conversationId));
    }

    @PatchMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> rename(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long conversationId,
                                                     @Valid @RequestBody UpdateConversationRequest request) {
        return ApiResponse.ok(conversations.rename(userId, conversationId, request.title()));
    }

    @PutMapping("/{conversationId}/knowledge-bases")
    public ApiResponse<ConversationResponse> knowledgeBases(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long conversationId,
                                                            @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.ok(conversations.replaceKnowledgeBases(
                userId, conversationId, request.knowledgeBaseIds()));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long conversationId) {
        conversations.delete(userId, conversationId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> messages(@AuthenticationPrincipal Long userId,
                                                       @PathVariable Long conversationId) {
        return ApiResponse.ok(conversations.messages(userId, conversationId));
    }

    @PostMapping(path = "/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@AuthenticationPrincipal Long userId,
                          @PathVariable Long conversationId,
                          @Valid @RequestBody AskRequest request) {
        return generation.ask(userId, conversationId, request.question());
    }

    @PostMapping("/{conversationId}/cancel")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long conversationId) {
        generation.cancel(userId, conversationId);
        return ApiResponse.ok(null);
    }
}
