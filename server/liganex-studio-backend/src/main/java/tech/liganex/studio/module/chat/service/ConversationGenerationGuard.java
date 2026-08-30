package tech.liganex.studio.module.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationGenerationGuard {
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private final StringRedisTemplate redis;

    public Lease acquire(Long ownerUserId, Long conversationId) {
        String key = lockKey(ownerUserId, conversationId);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该会话正在生成回答，请稍后再试");
        }
        redis.delete(cancelKey(ownerUserId, conversationId));
        return new Lease(key, token);
    }

    public void cancel(Long ownerUserId, Long conversationId) {
        redis.opsForValue().set(cancelKey(ownerUserId, conversationId), "1", LOCK_TTL);
    }

    public boolean cancelled(Long ownerUserId, Long conversationId) {
        return Boolean.TRUE.equals(redis.hasKey(cancelKey(ownerUserId, conversationId)));
    }

    public void release(Lease lease) {
        redis.execute(RELEASE, List.of(lease.key()), lease.token());
    }

    private static String lockKey(Long owner, Long conversation) {
        return "liganex:chat:lock:" + owner + ":" + conversation;
    }

    private static String cancelKey(Long owner, Long conversation) {
        return "liganex:chat:cancel:" + owner + ":" + conversation;
    }

    public record Lease(String key, String token) {
    }
}
