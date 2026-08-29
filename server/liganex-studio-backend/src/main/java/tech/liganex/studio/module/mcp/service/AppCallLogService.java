package tech.liganex.studio.module.mcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.liganex.studio.module.openapp.entity.AppCallLog;
import tech.liganex.studio.module.openapp.mapper.AppCallLogMapper;

import java.time.Instant;

/**
 * MCP 调用审计落库（ADR-0002）。仅记录 appId/工具/权限/结果/耗时，绝不写 appsecret 明文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppCallLogService {

    private final AppCallLogMapper appCallLogMapper;

    public void audit(String appId, String tool, String permission, String result, long latencyMs) {
        try {
            AppCallLog log = new AppCallLog();
            log.setAppId(appId);
            log.setTool(tool);
            log.setPermission(permission);
            log.setResult(result);
            log.setLatencyMs((int) latencyMs);
            log.setCreatedAt(Instant.now());
            appCallLogMapper.insert(log);
        } catch (Exception ex) {
            // 审计失败不影响主流程，但必须留痕
            log.error("audit write failed appId={} tool={}", appId, tool, ex);
        }
    }
}
