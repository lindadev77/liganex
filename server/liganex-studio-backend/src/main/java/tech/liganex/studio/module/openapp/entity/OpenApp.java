package tech.liganex.studio.module.openapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 开放平台应用（表结构由 Flyway 管理，ADR-0005）。
 *
 * <p>appSecretEnc 为 AES-GCM 加密后的 appsecret（可逆，用于 MCP HMAC 验签，见 V5 迁移）。
 * 明文 appsecret 仅在创建时一次性返回调用方，库内不存明文。
 */
@Data
@TableName("open_app")
public class OpenApp {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appId;

    private String appSecretEnc;

    private String name;

    private Long ownerUserId;

    /** ACTIVE | DISABLED */
    private String status;

    private Instant createdAt;

    private Instant updatedAt;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
