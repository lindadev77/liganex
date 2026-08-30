package tech.liganex.studio.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tech.liganex.studio.module.ai.AiModelClient;
import tech.liganex.studio.module.chat.entity.ChatSummary;
import tech.liganex.studio.module.chat.mapper.ChatMessageMapper;
import tech.liganex.studio.module.chat.mapper.ChatSummaryMapper;
import tech.liganex.studio.module.rag.config.RagProperties;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {
    private final ChatMessageMapper messageMapper;
    private final ChatSummaryMapper summaryMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AiModelClient models;
    private final RagProperties properties;

    public String currentSummary(Long ownerUserId, Long conversationId) {
        ChatSummary summary = summaryMapper.selectOne(new LambdaQueryWrapper<ChatSummary>()
                .eq(ChatSummary::getOwnerUserId, ownerUserId)
                .eq(ChatSummary::getConversationId, conversationId)
                .orderByDesc(ChatSummary::getCoveredThroughSequence)
                .last("LIMIT 1"));
        return summary == null ? "" : summary.getContent();
    }

    /** L2 update is best-effort; L0 messages are never changed or deleted. */
    public void summarizeIfNeeded(Long ownerUserId, Long conversationId) {
        if (!models.chatReady()) {
            return;
        }
        ChatSummary current = summaryMapper.selectOne(new LambdaQueryWrapper<ChatSummary>()
                .eq(ChatSummary::getOwnerUserId, ownerUserId)
                .eq(ChatSummary::getConversationId, conversationId)
                .orderByDesc(ChatSummary::getCoveredThroughSequence)
                .last("LIMIT 1"));
        long covered = current == null ? 0L : current.getCoveredThroughSequence();
        List<tech.liganex.studio.module.chat.entity.ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<tech.liganex.studio.module.chat.entity.ChatMessage>()
                        .eq(tech.liganex.studio.module.chat.entity.ChatMessage::getOwnerUserId, ownerUserId)
                        .eq(tech.liganex.studio.module.chat.entity.ChatMessage::getConversationId, conversationId)
                        .eq(tech.liganex.studio.module.chat.entity.ChatMessage::getStatus, "COMPLETED")
                        .gt(tech.liganex.studio.module.chat.entity.ChatMessage::getSequence, covered)
                        .orderByAsc(tech.liganex.studio.module.chat.entity.ChatMessage::getSequence)
                        .last("LIMIT " + properties.getMemory().getSummarySourceLimit()));
        if (messages.size() < properties.getMemory().getSummaryTriggerCount()) {
            return;
        }
        StringBuilder source = new StringBuilder();
        if (current != null && current.getContent() != null) {
            source.append("已有摘要：").append(current.getContent()).append("\n\n");
        }
        messages.forEach(message -> source.append(message.getRole()).append(": ")
                .append(message.getContent()).append('\n'));
        try {
            List<ChatMessage> prompt = List.of(
                    SystemMessage.from("请把对话压缩成忠实、简洁的滚动摘要，保留事实、约束和未解决问题；不要添加新事实。"),
                    UserMessage.from(source.toString()));
            String summary = models.chat(prompt);
            long through = messages.getLast().getSequence();
            jdbcTemplate.update("""
                    INSERT INTO chat_summary(owner_user_id, conversation_id, content,
                                             covered_through_sequence, created_at, updated_at)
                    VALUES (?, ?, ?, ?, now(), now())
                    ON CONFLICT (owner_user_id, conversation_id) DO UPDATE SET
                      content = EXCLUDED.content,
                      covered_through_sequence = EXCLUDED.covered_through_sequence,
                      updated_at = now()
                    """, ownerUserId, conversationId, summary, through);
        } catch (Exception ex) {
            log.warn("conversation summary failed owner={} conversation={}", ownerUserId, conversationId);
        }
    }
}
