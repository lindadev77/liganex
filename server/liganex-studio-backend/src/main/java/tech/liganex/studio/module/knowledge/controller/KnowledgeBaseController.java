package tech.liganex.studio.module.knowledge.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.knowledge.dto.CreateKnowledgeBaseRequest;
import tech.liganex.studio.module.knowledge.dto.KnowledgeBaseResponse;
import tech.liganex.studio.module.knowledge.dto.UpdateKnowledgeBaseRequest;
import tech.liganex.studio.module.knowledge.service.KnowledgeBaseService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeBaseResponse> create(
            @AuthenticationPrincipal Long ownerUserId,
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.create(ownerUserId, request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list(
            @AuthenticationPrincipal Long ownerUserId) {
        return ApiResponse.ok(knowledgeBaseService.list(ownerUserId));
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseResponse> get(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId) {
        return ApiResponse.ok(knowledgeBaseService.get(ownerUserId, knowledgeBaseId));
    }

    @PutMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseResponse> update(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.update(ownerUserId, knowledgeBaseId, request));
    }

    @DeleteMapping("/{knowledgeBaseId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId) {
        knowledgeBaseService.delete(ownerUserId, knowledgeBaseId);
        return ApiResponse.ok(null);
    }
}
