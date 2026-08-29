package tech.liganex.studio.module.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT 配置。密钥来自环境变量 {@code LIGANEX_JWT_SECRET}，无默认值 —— 未注入时启动即失败（ADR-0007）。
 */
@ConfigurationProperties(prefix = "liganex.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32; // HS256 要求

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "LIGANEX_JWT_SECRET 未配置：JWT 密钥必须由环境变量或启动参数注入，禁止写入配置文件（ADR-0007）。"
                            + "生成方式：openssl rand -base64 48");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT 密钥过短：HS256 至少需要 " + MIN_SECRET_BYTES + " 字节");
        }
    }
}
