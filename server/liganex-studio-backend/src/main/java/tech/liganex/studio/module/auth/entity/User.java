package tech.liganex.studio.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * B 端用户。表结构由 Flyway 管理（ADR-0005），本实体只做 DML 映射。
 */
@Data
@TableName("app_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    /** BCrypt 哈希，任何情况下不得为明文 */
    private String passwordHash;

    private String displayName;

    /** ACTIVE | DISABLED */
    private String status;

    private Instant createdAt;

    private Instant updatedAt;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
