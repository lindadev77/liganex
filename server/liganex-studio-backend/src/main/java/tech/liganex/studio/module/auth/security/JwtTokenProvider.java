package tech.liganex.studio.module.auth.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * JWT 签发与校验（对称密钥 HS256）。
 *
 * <p>access token 用于访问 {@code /api/v1/**}；refresh token 仅用于换取新的 access token，
 * 两者以 {@code typ} 声明区分，不可互换。
 */
@Component
public class JwtTokenProvider {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        SecretKey key = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).build();
    }

    public String generateAccessToken(long userId, String email) {
        return generate(userId, email, "access", properties.accessTokenTtl());
    }

    public String generateRefreshToken(long userId) {
        return generate(userId, null, "refresh", properties.refreshTokenTtl());
    }

    /** 解析并校验签名与有效期，失败抛出 JwtException（由调用方转 401）。 */
    public Jwt parse(String token) {
        return decoder.decode(token);
    }

    public boolean isAccessToken(Jwt jwt) {
        return "access".equals(jwt.getClaimAsString("typ"));
    }

    public long extractUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private String generate(long userId, String email, String type, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("typ", type);
        if (email != null) {
            claims.claim("email", email);
        }
        // 必须显式声明 HS256：NimbusJwtEncoder 依赖 header 中的算法去选择 JWK 签名密钥，
        // 否则报 "Failed to select a JWK signing key"
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
