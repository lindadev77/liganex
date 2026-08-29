package tech.liganex.studio.module.mcp.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.common.security.AppSecretCipher;
import tech.liganex.studio.module.mcp.service.McpQuotaService;
import tech.liganex.studio.module.openapp.entity.AppPermission;
import tech.liganex.studio.module.openapp.entity.OpenApp;
import tech.liganex.studio.module.openapp.mapper.AppPermissionMapper;
import tech.liganex.studio.module.openapp.mapper.OpenAppMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * MCP 应用签名鉴权核心。
 *
 * <p>校验顺序（兼顾安全与效率）：时间戳窗口 → HMAC 签名 → nonce 防重放 → scope 授权 → 配额。
 * 任一环节失败抛出 {@link BizException}，由控制器转换为 JSON-RPC 错误并审计。
 *
 * <p>三类鉴权体系互不通用（ADR-0002/0009）：本类负责「应用凭证(MCP/skill)」一侧。
 */
@Slf4j
@Service
public class McpAuthService {

    private final OpenAppMapper openAppMapper;
    private final AppPermissionMapper appPermissionMapper;
    private final AppSecretCipher appSecretCipher;
    private final McpQuotaService quotaService;

    @Value("${liganex.mcp.timestamp-window-sec:300}")
    private long timestampWindowSec;

    @Value("${liganex.mcp.default-quota-per-month:10000}")
    private long defaultQuota;

    public McpAuthService(OpenAppMapper openAppMapper,
                          AppPermissionMapper appPermissionMapper,
                          AppSecretCipher appSecretCipher,
                          McpQuotaService quotaService) {
        this.openAppMapper = openAppMapper;
        this.appPermissionMapper = appPermissionMapper;
        this.appSecretCipher = appSecretCipher;
        this.quotaService = quotaService;
    }

    public McpAuthContext verify(McpAuthRequest req) {
        // 1) 头部齐备性
        if (isBlank(req.timestamp()) || isBlank(req.nonce()) || isBlank(req.signature())) {
            throw new BizException(ErrorCode.SIGNATURE_INVALID, "缺少时间戳/nonce/签名头");
        }

        // 2) 应用存在且启用
        OpenApp app = openAppMapper.selectOne(
                new LambdaQueryWrapper<OpenApp>().eq(OpenApp::getAppId, req.appId()));
        if (app == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND);
        }
        if (!app.isActive()) {
            throw new BizException(ErrorCode.APP_DISABLED);
        }

        // 3) 时间戳窗口（±window 秒）
        long ts;
        try {
            ts = Long.parseLong(req.timestamp());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.TIMESTAMP_EXPIRED, "时间戳格式错误");
        }
        long nowSec = Instant.now().getEpochSecond();
        if (Math.abs(nowSec - ts) > timestampWindowSec) {
            throw new BizException(ErrorCode.TIMESTAMP_EXPIRED);
        }

        // 4) 还原明文 appsecret 并验签
        String secret;
        try {
            secret = appSecretCipher.decrypt(app.getAppSecretEnc());
        } catch (RuntimeException ex) {
            log.error("app secret 解密失败 appId={}", app.getAppId(), ex);
            throw new BizException(ErrorCode.APP_SECRET_BROKEN);
        }
        String expected = hmacSha256Hex(secret, canonical(req));
        if (!constantTimeEquals(expected, req.signature())) {
            log.info("MCP signature mismatch appId={}", app.getAppId());
            throw new BizException(ErrorCode.SIGNATURE_INVALID);
        }

        // 5) nonce 防重放（签名有效后才消耗 nonce，避免错误签名烧掉 nonce）
        quotaService.checkNonce(app.getAppId(), req.nonce(), timestampWindowSec);

        // 6) scope 授权
        List<String> scopes = appPermissionMapper.selectList(
                        new LambdaQueryWrapper<AppPermission>()
                                .eq(AppPermission::getAppId, app.getAppId()))
                .stream().map(AppPermission::getPermissionCode).toList();
        if (!scopes.contains(req.requiredScope())) {
            throw new BizException(ErrorCode.SCOPE_FORBIDDEN);
        }

        // 7) 配额
        quotaService.consumeQuota(app.getAppId(), defaultQuota);

        return new McpAuthContext(app.getAppId(), scopes);
    }

    private String canonical(McpAuthRequest req) {
        return String.join("\n",
                req.method(),
                req.path(),
                req.timestamp(),
                req.nonce(),
                req.body() == null ? "" : req.body());
    }

    private String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
