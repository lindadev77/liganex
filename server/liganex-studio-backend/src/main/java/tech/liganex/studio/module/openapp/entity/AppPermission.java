package tech.liganex.studio.module.openapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 应用-权限绑定（应用拥有的权限集）。复合主键 (app_id, permission_code) 由 DB 约束保证。
 */
@Data
@TableName("app_permission")
public class AppPermission {

    @TableId(value = "app_id", type = IdType.INPUT)
    private String appId;

    private String permissionCode;

    private Instant createdAt;
}
