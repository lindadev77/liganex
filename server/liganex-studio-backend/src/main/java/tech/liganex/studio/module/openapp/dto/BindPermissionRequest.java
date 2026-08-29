package tech.liganex.studio.module.openapp.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 绑定权限集请求：指定要授予应用的权限 code 列表（如 ["order:read"]）。
 */
public record BindPermissionRequest(
        @NotEmpty @Size(max = 64, message = "单次绑定权限数过多") List<String> permissionCodes) {
}
