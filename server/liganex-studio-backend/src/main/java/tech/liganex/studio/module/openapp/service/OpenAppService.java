package tech.liganex.studio.module.openapp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.common.security.AppSecretCipher;
import tech.liganex.studio.module.openapp.dto.AppResponse;
import tech.liganex.studio.module.openapp.dto.AppSecretResponse;
import tech.liganex.studio.module.openapp.dto.PermissionDTO;
import tech.liganex.studio.module.openapp.entity.AppPermission;
import tech.liganex.studio.module.openapp.entity.OpenApp;
import tech.liganex.studio.module.openapp.entity.Permission;
import tech.liganex.studio.module.openapp.mapper.AppPermissionMapper;
import tech.liganex.studio.module.openapp.mapper.OpenAppMapper;
import tech.liganex.studio.module.openapp.mapper.PermissionMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 开放平台应用与权限集服务。
 *
 * <p>创建应用时生成 appsecret，明文仅在此处一次性返回；库内存 AES 密文（见 {@link AppSecretCipher}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAppService {

    private final OpenAppMapper openAppMapper;
    private final PermissionMapper permissionMapper;
    private final AppPermissionMapper appPermissionMapper;
    private final AppSecretCipher appSecretCipher;

    @Transactional
    public AppSecretResponse createApp(Long ownerUserId, String name) {
        String appId = "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String secret = AppSecretCipher.generateSecret();

        OpenApp app = new OpenApp();
        app.setAppId(appId);
        app.setAppSecretEnc(appSecretCipher.encrypt(secret));
        app.setName(name);
        app.setOwnerUserId(ownerUserId);
        app.setStatus("ACTIVE");
        app.setCreatedAt(Instant.now());
        app.setUpdatedAt(Instant.now());
        openAppMapper.insert(app);

        log.info("open app created appId={} owner={}", appId, ownerUserId);
        // 明文仅此一次返回
        return new AppSecretResponse(appId, secret);
    }

    @Transactional
    public void bindPermissions(Long ownerUserId, String appId, List<String> permissionCodes) {
        OpenApp app = requireOwnedApp(ownerUserId, appId);

        for (String code : permissionCodes) {
            Permission permission = permissionMapper.selectById(code);
            if (permission == null) {
                throw new BizException(ErrorCode.PERMISSION_NOT_FOUND);
            }
            long bound = appPermissionMapper.selectCount(
                    new LambdaQueryWrapper<AppPermission>()
                            .eq(AppPermission::getAppId, appId)
                            .eq(AppPermission::getPermissionCode, code));
            if (bound == 0) {
                AppPermission ap = new AppPermission();
                ap.setAppId(appId);
                ap.setPermissionCode(code);
                ap.setCreatedAt(Instant.now());
                appPermissionMapper.insert(ap);
            }
        }
        log.info("app permissions bound appId={} codes={}", appId, permissionCodes);
    }

    public List<AppResponse> listMyApps(Long ownerUserId) {
        return openAppMapper.selectList(
                        new LambdaQueryWrapper<OpenApp>().eq(OpenApp::getOwnerUserId, ownerUserId))
                .stream().map(AppResponse::from).toList();
    }

    public AppResponse getApp(Long ownerUserId, String appId) {
        OpenApp app = requireOwnedApp(ownerUserId, appId);
        return AppResponse.from(app);
    }

    public List<PermissionDTO> listPermissions() {
        return permissionMapper.selectList(null).stream().map(PermissionDTO::from).toList();
    }

    public List<String> getAppPermissions(Long ownerUserId, String appId) {
        requireOwnedApp(ownerUserId, appId);
        return appPermissionMapper.selectList(
                        new LambdaQueryWrapper<AppPermission>().eq(AppPermission::getAppId, appId))
                .stream().map(AppPermission::getPermissionCode).toList();
    }

    /**
     * 校验应用归属并返回实体；不归属当前用户统一返回 APP_NOT_FOUND，避免应用枚举。
     */
    private OpenApp requireOwnedApp(Long ownerUserId, String appId) {
        OpenApp app = openAppMapper.selectOne(
                new LambdaQueryWrapper<OpenApp>().eq(OpenApp::getAppId, appId));
        if (app == null || !app.getOwnerUserId().equals(ownerUserId)) {
            throw new BizException(ErrorCode.APP_NOT_FOUND);
        }
        return app;
    }
}
