package tech.liganex.studio.module.openapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAppRequest(
        @NotBlank @Size(max = 128, message = "应用名称最长 128 字符") String name) {
}
