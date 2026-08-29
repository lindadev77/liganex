package tech.liganex.studio.module.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.openapp.entity.QuotaUsage;
import tech.liganex.studio.module.openapp.mapper.QuotaUsageMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

/**
 * MCP 调用限额与防重放。
 *
 * <ul>
 *   <li>nonce：Redis {@code SET NX} 去重，TTL 取时间窗两倍，命中即视为重放（ADR-0002）。</li>
 *   <li>配额：DB {@code quota_usage} 按月计数，行锁（FOR UPDATE）避免并发超发。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpQuotaService {

    private final QuotaUsageMapper quotaUsageMapper;
    private final StringRedisTemplate redisTemplate;

    public void checkNonce(String appId, String nonce, long windowSec) {
        String key = "mcp:nonce:" + appId + ":" + nonce;
        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(windowSec * 2));
        if (Boolean.FALSE.equals(absent)) {
            log.info("nonce replay detected appId={} nonce={}", appId, nonce);
            throw new BizException(ErrorCode.NONCE_REPLAY);
        }
    }

    @Transactional
    public void consumeQuota(String appId, long limit) {
        String period = currentMonth();
        QuotaUsage existing = quotaUsageMapper.selectOne(
                new LambdaQueryWrapper<QuotaUsage>()
                        .eq(QuotaUsage::getAppId, appId)
                        .eq(QuotaUsage::getPeriod, period)
                        .last("FOR UPDATE"));

        if (existing == null) {
            QuotaUsage created = new QuotaUsage();
            created.setAppId(appId);
            created.setPeriod(period);
            created.setUsed(1L);
            created.setUpdatedAt(Instant.now());
            quotaUsageMapper.insert(created);
            return;
        }
        if (existing.getUsed() != null && existing.getUsed() >= limit) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED);
        }
        quotaUsageMapper.update(null, new LambdaUpdateWrapper<QuotaUsage>()
                .eq(QuotaUsage::getAppId, appId)
                .eq(QuotaUsage::getPeriod, period)
                .set(QuotaUsage::getUsed, existing.getUsed() == null ? 1L : existing.getUsed() + 1)
                .set(QuotaUsage::getUpdatedAt, Instant.now()));
    }

    private String currentMonth() {
        return DateTimeFormatter.ofPattern("yyyy-MM")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
    }
}
