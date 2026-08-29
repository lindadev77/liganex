package tech.liganex.studio.module.openapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 权限字典（{resource}:{action}），如 order:read。
 */
@Data
@TableName("permission")
public class Permission {

    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    private String name;

    private String description;

    /**
     * 是否已开放（后端已落地真实接口的权限才为 true）。前端据此只展示已做好的权限项。
     */
    private Boolean opened;
}
