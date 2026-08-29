package tech.liganex.studio.module.openapp.dto;

import tech.liganex.studio.module.openapp.entity.OpenApp;

import java.time.Instant;

/**
 * 应用对外展示（不含 appsecret 明文/密文）。
 */
public record AppResponse(String appId, String name, String status, Instant createdAt) {

    public static AppResponse from(OpenApp app) {
        return new AppResponse(app.getAppId(), app.getName(), app.getStatus(), app.getCreatedAt());
    }
}
