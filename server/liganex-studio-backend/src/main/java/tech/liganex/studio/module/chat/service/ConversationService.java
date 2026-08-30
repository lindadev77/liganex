package tech.liganex.studio.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.chat.dto.ChatDtos.ConversationResponse;
import tech.liganex.studio.module.chat.dto.ChatDtos.MessageResponse;
import tech.liganex.studio.module.chat.entity.ChatConversation;
import tech.liganex.studio.module.chat.entity.ChatMessage;
import tech.liganex.studio.module.chat.mapper.ChatConversationMapper;
import tech.liganex.studio.module.chat.mapper.ChatMessageMapper;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ConversationResponse create(Long ownerUserId, String title, List<Long> knowledgeBaseIds) {
        List<Long> normalized = normalizeKnowledgeBases(ownerUserId, knowledgeBaseIds);
        ChatConversation conversation = new ChatConversation();
        conversation.setOwnerUserId(ownerUserId);
        conversation.setTitle(title == null || title.isBlank() ? "新会话" : title.trim());
        conversation.setStatus("ACTIVE");
        conversation.setNextMessageSequence(1L);
        conversation.setCreatedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
        conversationMapper.insert(conversation);
        replaceKnowledgeBaseRows(ownerUserId, conversation.getId(), normalized);
        return ConversationResponse.from(conversation, normalized);
    }

    public List<ConversationResponse> list(Long ownerUserId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getOwnerUserId, ownerUserId)
                        .eq(ChatConversation::getStatus, "ACTIVE")
                        .orderByDesc(ChatConversation::getUpdatedAt))
                .stream().map(value -> ConversationResponse.from(value, knowledgeBaseIds(ownerUserId, value.getId())))
                .toList();
    }

    public ConversationResponse get(Long ownerUserId, Long conversationId) {
        ChatConversation value = requireOwned(ownerUserId, conversationId);
        return ConversationResponse.from(value, knowledgeBaseIds(ownerUserId, conversationId));
    }

    @Transactional
    public ConversationResponse rename(Long ownerUserId, Long conversationId, String title) {
        ChatConversation value = requireOwned(ownerUserId, conversationId);
        value.setTitle(title.trim());
        value.setUpdatedAt(Instant.now());
        conversationMapper.updateById(value);
        return ConversationResponse.from(value, knowledgeBaseIds(ownerUserId, conversationId));
    }

    @Transactional
    public ConversationResponse replaceKnowledgeBases(Long ownerUserId, Long conversationId, List<Long> ids) {
        ChatConversation value = requireOwned(ownerUserId, conversationId);
        List<Long> normalized = normalizeKnowledgeBases(ownerUserId, ids);
        replaceKnowledgeBaseRows(ownerUserId, conversationId, normalized);
        value.setUpdatedAt(Instant.now());
        conversationMapper.updateById(value);
        return ConversationResponse.from(value, normalized);
    }

    @Transactional
    public void delete(Long ownerUserId, Long conversationId) {
        requireOwned(ownerUserId, conversationId);
        jdbcTemplate.update("DELETE FROM chat_conversation WHERE id = ? AND owner_user_id = ?",
                conversationId, ownerUserId);
    }

    public List<MessageResponse> messages(Long ownerUserId, Long conversationId) {
        requireOwned(ownerUserId, conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getOwnerUserId, ownerUserId)
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getSequence))
                .stream().map(MessageResponse::from).toList();
    }

    public ChatConversation requireOwned(Long ownerUserId, Long conversationId) {
        ChatConversation value = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getOwnerUserId, ownerUserId)
                .eq(ChatConversation::getStatus, "ACTIVE"));
        if (value == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return value;
    }

    public List<Long> knowledgeBaseIds(Long ownerUserId, Long conversationId) {
        return jdbcTemplate.queryForList("""
                        SELECT knowledge_base_id FROM chat_conversation_kb
                        WHERE owner_user_id = ? AND conversation_id = ? ORDER BY knowledge_base_id
                        """, Long.class, ownerUserId, conversationId);
    }

    private List<Long> normalizeKnowledgeBases(Long ownerUserId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个知识库");
        }
        List<Long> normalized = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个知识库");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(normalized.size(), "?"));
        Object[] parameters = new Object[normalized.size() + 1];
        parameters[0] = ownerUserId;
        for (int i = 0; i < normalized.size(); i++) {
            parameters[i + 1] = normalized.get(i);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM knowledge_base WHERE owner_user_id = ? AND status = 'ACTIVE' AND id IN ("
                        + placeholders + ")",
                Integer.class, parameters);
        if (count == null || count != normalized.size()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return normalized;
    }

    private void replaceKnowledgeBaseRows(Long ownerUserId, Long conversationId, List<Long> ids) {
        jdbcTemplate.update("DELETE FROM chat_conversation_kb WHERE owner_user_id = ? AND conversation_id = ?",
                ownerUserId, conversationId);
        for (Long id : ids) {
            jdbcTemplate.update("""
                    INSERT INTO chat_conversation_kb(owner_user_id, conversation_id, knowledge_base_id, created_at)
                    VALUES (?, ?, ?, now())
                    """, ownerUserId, conversationId, id);
        }
    }
}
