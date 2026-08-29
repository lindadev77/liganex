package tech.liganex.studio.module.auth.dto;

/**
 * 令牌响应。access token 短期、refresh token 长期，两者以 typ 声明区分，不可互换。
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
