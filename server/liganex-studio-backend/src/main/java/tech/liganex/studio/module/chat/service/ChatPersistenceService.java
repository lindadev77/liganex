package tech.liganex.studio.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.chat.entity.ChatConversation;
import tech.liganex.studio.module.chat.entity.ChatMessage;
import tech.liganex.studio.module.chat.mapper.ChatConversationMapper;
import tech.liganex.studio.module.chat.mapper.ChatMessageMapper;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatPersistenceService {
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public PendingGeneration prepare(Long ownerUserId, Long conversationId, String question) {
        ChatConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getOwnerUserId, ownerUserId)
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getStatus, "ACTIVE")
                .last("FOR UPDATE"));
        if (conversation == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        long userSequence = conversation.getNextMessageSequence();
        long assistantSequence = userSequence + 1;
        conversation.setNextMessageSequence(assistantSequence + 1);
        conversation.setUpdatedAt(Instant.now());
        conversationMapper.updateById(conversation);

        ChatMessage user = message(ownerUserId, conversationId, userSequence, "USER", question, "COMPLETED");
        user.setCompletedAt(Instant.now());
        messageMapper.insert(user);
        ChatMessage assistant = message(ownerUserId, conversationId, assistantSequence, "ASSISTANT", "", "GENERATING");
        messageMapper.insert(assistant);
        return new PendingGeneration(user, assistant);
    }

    @Transactional
    public void complete(Long ownerUserId, Long messageId, String answer, String citations) {
        int updated = jdbcTemplate.update("""
                UPDATE chat_message SET content = ?, citations = CAST(? AS jsonb), status = 'COMPLETED', completed_at = now()
                WHERE id = ? AND owner_user_id = ? AND status = 'GENERATING'
                """, answer, citations, messageId, ownerUserId);
        if (updated != 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    public void mark(Long ownerUserId, Long messageId, String status, String partialContent) {
        jdbcTemplate.update("""
                UPDATE chat_message SET content = ?, status = ?, completed_at = now()
                WHERE id = ? AND owner_user_id = ? AND status = 'GENERATING'
                """, partialContent, status, messageId, ownerUserId);
    }

    public List<ChatMessage> recent(Long ownerUserId, Long conversationId, int limit) {
        List<ChatMessage> descending = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getOwnerUserId, ownerUserId)
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getStatus, "COMPLETED")
                .orderByDesc(ChatMessage::getSequence)
                .last("LIMIT " + Math.max(1, limit)));
        return descending.reversed();
    }

    private static ChatMessage message(Long ownerUserId, Long conversationId, long sequence,
                                       String role, String content, String status) {
        ChatMessage value = new ChatMessage();
        value.setOwnerUserId(ownerUserId);
        value.setConversationId(conversationId);
        value.setSequence(sequence);
        value.setRole(role);
        value.setContent(content);
        value.setStatus(status);
        value.setCreatedAt(Instant.now());
        return value;
    }

    public record PendingGeneration(ChatMessage userMessage, ChatMessage assistantMessage) {
    }
}
