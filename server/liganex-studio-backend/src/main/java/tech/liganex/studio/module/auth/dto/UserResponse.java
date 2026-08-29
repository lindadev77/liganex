package tech.liganex.studio.module.auth.dto;

import tech.liganex.studio.module.auth.entity.User;

/**
 * 用户出参：不含 passwordHash 等任何凭证字段。
 */
public record UserResponse(Long id, String email, String displayName, String status) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus());
    }
}
