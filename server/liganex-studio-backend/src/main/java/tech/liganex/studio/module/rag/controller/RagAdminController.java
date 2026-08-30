package tech.liganex.studio.module.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.rag.index.IndexRebuildService;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/index")
@RequiredArgsConstructor
public class RagAdminController {
    private final IndexRebuildService rebuildService;

    @PostMapping("/rebuild")
    public ApiResponse<RebuildResponse> rebuild(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long knowledgeBaseId) {
        return ApiResponse.ok(new RebuildResponse(rebuildService.rebuild(userId, knowledgeBaseId)));
    }

    public record RebuildResponse(int queuedDocuments) {
    }
}
