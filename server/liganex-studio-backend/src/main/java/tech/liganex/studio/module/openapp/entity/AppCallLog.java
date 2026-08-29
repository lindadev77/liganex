package tech.liganex.studio.module.openapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MCP / 开放平台调用审计（ADR-0002）。禁止记录 appsecret 明文，result 仅记成功/失败原因。
 */
@Data
@TableName("app_call_log")
public class AppCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appId;

    private String tool;

    private String permission;

    private String result;

    private Integer latencyMs;

    private Instant createdAt;
}
