package tech.liganex.studio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 服务间调用鉴权（/internal/** 链路，区别于用户 JWT 与 MCP 应用签名）。
 *
 * <p>校验请求头 {@code X-Internal-Api-Key} 是否等于注入的服务间凭证（ADR-0007：必须由环境变量注入，无默认值）。
 * 凭证未配置或为空时一律拒绝，避免「空密钥即放行」的致命漏洞。
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@org.springframework.beans.factory.annotation.Value("${liganex.internal.service-api-key:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            log.error("internal api key 未配置（LIGANEX_INTERNAL_API_KEY），拒绝所有 /internal 请求");
            reject(response);
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, expectedApiKey)) {
            log.info("internal api key mismatch path={}", request.getRequestURI());
            reject(response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("internal-service", null, List.of()));
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":45001,\"message\":\"internal api key invalid\",\"data\":null}");
    }

    /** 常量时间比较，降低时序侧信道风险 */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
