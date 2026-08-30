package tech.liganex.studio.module.knowledge.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Import;
import tech.liganex.studio.support.AuthenticationPrincipalTestConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.auth.security.JwtTokenProvider;
import tech.liganex.studio.module.knowledge.dto.CreateTextDocumentRequest;
import tech.liganex.studio.module.knowledge.dto.KnowledgeDocumentResponse;
import tech.liganex.studio.module.knowledge.service.KnowledgeDocumentService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AuthenticationPrincipalTestConfig.class)
@WebMvcTest(KnowledgeDocumentController.class)
class KnowledgeDocumentControllerTest {
    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(42L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @MockitoBean
    private KnowledgeDocumentService service;

    // 同 KnowledgeBaseControllerTest：只 mock JWT 过滤器依赖的 TokenProvider
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createTextUsesAuthenticatedPrincipalAsOwner() throws Exception {
        when(service.createText(eq(42L), eq(7L), any(CreateTextDocumentRequest.class))).thenReturn(document());

        mvc.perform(post("/api/v1/knowledge-bases/7/documents/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"说明\",\"content\":\"可检索内容\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(88));

        verify(service).createText(eq(42L), eq(7L), any(CreateTextDocumentRequest.class));
    }

    @Test
    void uploadUsesAuthenticatedPrincipalAsOwner() throws Exception {
        when(service.upload(eq(42L), eq(7L), eq("说明"), any())).thenReturn(document());

        mvc.perform(multipart("/api/v1/knowledge-bases/7/documents/upload")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "内容".getBytes()))
                        .param("title", "说明"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(service).upload(eq(42L), eq(7L), eq("说明"), any());
    }

    @Test
    void validationFailureReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/knowledge-bases/7/documents/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.code()));
    }

    @Test
    void crossUserDocumentLooksMissing() throws Exception {
        when(service.retry(42L, 7L, 88L)).thenThrow(new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));

        mvc.perform(post("/api/v1/knowledge-bases/7/documents/88/retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND.code()));
    }

    private static KnowledgeDocumentResponse document() {
        return new KnowledgeDocumentResponse(88L, 7L, "说明", "TEXT", "text/plain",
                null, 12L, "PENDING", 0, 0, null, null, null, Instant.now(), Instant.now());
    }
}
