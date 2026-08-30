package tech.liganex.studio.module.knowledge.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.knowledge.dto.CreateTextDocumentRequest;
import tech.liganex.studio.module.knowledge.dto.KnowledgeDocumentResponse;
import tech.liganex.studio.module.knowledge.service.KnowledgeDocumentService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeDocumentResponse> createText(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody CreateTextDocumentRequest request) {
        return ApiResponse.ok(knowledgeDocumentService.createText(ownerUserId, knowledgeBaseId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeDocumentResponse> upload(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId,
            @RequestParam(required = false) String title,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(knowledgeDocumentService.upload(ownerUserId, knowledgeBaseId, title, file));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeDocumentResponse>> list(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId) {
        return ApiResponse.ok(knowledgeDocumentService.list(ownerUserId, knowledgeBaseId));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<KnowledgeDocumentResponse> get(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        return ApiResponse.ok(knowledgeDocumentService.get(ownerUserId, knowledgeBaseId, documentId));
    }

    @PostMapping("/{documentId}/retry")
    public ApiResponse<KnowledgeDocumentResponse> retry(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        return ApiResponse.ok(knowledgeDocumentService.retry(ownerUserId, knowledgeBaseId, documentId));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        knowledgeDocumentService.delete(ownerUserId, knowledgeBaseId, documentId);
        return ApiResponse.ok(null);
    }
}
