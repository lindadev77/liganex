package tech.liganex.studio.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "邮箱格式不正确") String email,
        @NotBlank @Size(min = 8, max = 128, message = "密码长度需为 8-128 位") String password,
        String displayName) {
}
