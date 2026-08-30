package tech.liganex.studio.module.knowledge.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Import;
import tech.liganex.studio.support.AuthenticationPrincipalTestConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.auth.security.JwtTokenProvider;
import tech.liganex.studio.module.knowledge.dto.CreateKnowledgeBaseRequest;
import tech.liganex.studio.module.knowledge.dto.KnowledgeBaseResponse;
import tech.liganex.studio.module.knowledge.service.KnowledgeBaseService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AuthenticationPrincipalTestConfig.class)
@WebMvcTest(KnowledgeBaseController.class)
class KnowledgeBaseControllerTest {
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
    private KnowledgeBaseService service;

    // 不发送 Authorization 头，真实 JWT 过滤器会直接放行；这里只提供其依赖，避免加载 JWT 编码器链
    
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createUsesAuthenticatedPrincipalAsOwner() throws Exception {
        when(service.create(eq(42L), any(CreateKnowledgeBaseRequest.class))).thenReturn(
                new KnowledgeBaseResponse(7L, "手册", null, "ACTIVE", Instant.now(), Instant.now()));

        mvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"手册\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7));

        verify(service).create(eq(42L), any(CreateKnowledgeBaseRequest.class));
    }

    @Test
    void validationFailureReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.code()));
    }

    @Test
    void foreignResourceLooksLikeMissingResource() throws Exception {
        when(service.get(42L, 99L)).thenThrow(new BizException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));

        mvc.perform(get("/api/v1/knowledge-bases/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.code()));
    }
}
