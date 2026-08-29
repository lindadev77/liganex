package tech.liganex.studio.module.openapp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.openapp.dto.AppResponse;
import tech.liganex.studio.module.openapp.dto.AppSecretResponse;
import tech.liganex.studio.module.openapp.dto.BindPermissionRequest;
import tech.liganex.studio.module.openapp.dto.CreateAppRequest;
import tech.liganex.studio.module.openapp.dto.PermissionDTO;
import tech.liganex.studio.module.openapp.service.OpenAppService;

import java.util.List;

/**
 * 开放平台管理接口（用户 JWT 鉴权，/api/** 链路）。
 *
 * <p>应用创建后返回一次性明文 appsecret；权限集（如 order:read）绑定后供 MCP 调用方使用。
 */
@RestController
@RequestMapping("/api/v1/open")
@RequiredArgsConstructor
public class OpenAppController {

    private final OpenAppService openAppService;

    @PostMapping("/apps")
    public ApiResponse<AppSecretResponse> createApp(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateAppRequest request) {
        return ApiResponse.ok(openAppService.createApp(userId, request.name()));
    }

    @PostMapping("/apps/{appId}/permissions")
    public ApiResponse<Void> bindPermissions(
            @AuthenticationPrincipal Long userId,
            @PathVariable String appId,
            @Valid @RequestBody BindPermissionRequest request) {
        openAppService.bindPermissions(userId, appId, request.permissionCodes());
        return ApiResponse.ok(null);
    }

    @GetMapping("/apps")
    public ApiResponse<List<AppResponse>> listMyApps(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(openAppService.listMyApps(userId));
    }

    @GetMapping("/apps/{appId}")
    public ApiResponse<AppResponse> getApp(
            @AuthenticationPrincipal Long userId,
            @PathVariable String appId) {
        return ApiResponse.ok(openAppService.getApp(userId, appId));
    }

    @GetMapping("/apps/{appId}/permissions")
    public ApiResponse<List<String>> getAppPermissions(
            @AuthenticationPrincipal Long userId,
            @PathVariable String appId) {
        return ApiResponse.ok(openAppService.getAppPermissions(userId, appId));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionDTO>> listPermissions() {
        return ApiResponse.ok(openAppService.listPermissions());
    }
}
