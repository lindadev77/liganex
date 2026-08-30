package tech.liganex.studio.module.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.liganex.studio.module.ai.AiModelClient;
import tech.liganex.studio.module.chat.entity.ChatSummary;
import tech.liganex.studio.module.chat.mapper.ChatMessageMapper;
import tech.liganex.studio.module.chat.mapper.ChatSummaryMapper;
import tech.liganex.studio.module.rag.config.RagProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分层记忆：L0 完整历史不被动改，L2 滚动摘要按覆盖序号推进且失败不影响原消息。
 */
class ConversationMemoryServiceTest {

    private final ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
    private final ChatSummaryMapper summaryMapper = mock(ChatSummaryMapper.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AiModelClient models = mock(AiModelClient.class);

    private ConversationMemoryService service(RagProperties.Memory memory) {
        RagProperties properties = new RagProperties();
        properties.setMemory(memory);
        return new ConversationMemoryService(messageMapper, summaryMapper, jdbcTemplate, models, properties);
    }

    private RagProperties.Memory memory(int trigger, int sourceLimit) {
        RagProperties.Memory memory = new RagProperties.Memory();
        memory.setSummaryTriggerCount(trigger);
        memory.setSummarySourceLimit(sourceLimit);
        return memory;
    }

    private tech.liganex.studio.module.chat.entity.ChatMessage message(long sequence, String role, String content) {
        tech.liganex.studio.module.chat.entity.ChatMessage message =
                new tech.liganex.studio.module.chat.entity.ChatMessage();
        message.setOwnerUserId(7L);
        message.setConversationId(11L);
        message.setSequence(sequence);
        message.setRole(role);
        message.setContent(content);
        message.setStatus("COMPLETED");
        return message;
    }

    @Test
    void noSummaryBeforeTriggerCount() {
        when(summaryMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1L, "USER", "你好")));

        service(memory(24, 40)).summarizeIfNeeded(7L, 11L);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void writesRollingSummaryCoveringLatestSequence() {
        when(models.chatReady()).thenReturn(true);
        when(summaryMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1L, "USER", "订单怎么查"),
                message(2L, "ASSISTANT", "用 order_query")));
        when(models.chat(any(List.class))).thenReturn("用户询问订单查询方式。");

        service(memory(2, 40)).summarizeIfNeeded(7L, 11L);

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), args.capture(), args.capture(), args.capture(), args.capture());
        List<Object> values = args.getAllValues();
        assertThat(values.get(0)).isEqualTo(7L);          // owner 来自调用方，不是模型
        assertThat(values.get(1)).isEqualTo(11L);
        assertThat(values.get(2)).isEqualTo("用户询问订单查询方式。");
        assertThat(values.get(3)).isEqualTo(2L);          // 覆盖到最新一条消息
    }

    @Test
    void summaryFailureNeverTouchesMessages() {
        when(models.chatReady()).thenReturn(true);
        when(summaryMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1L, "USER", "a"), message(2L, "ASSISTANT", "b")));
        when(models.chat(any(List.class))).thenThrow(new IllegalStateException("model down"));

        service(memory(2, 40)).summarizeIfNeeded(7L, 11L);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(messageMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(messageMapper, never()).update(any(tech.liganex.studio.module.chat.entity.ChatMessage.class),
                any(LambdaQueryWrapper.class));
    }

    /**
     * owner/conversation 的查询条件由 LambdaQueryWrapper 构造，纯单测环境缺少 MyBatis-Plus 的
     * lambda 元数据缓存，无法展开 SQL；这里只验证取值行为，跨用户隔离由集成测试覆盖（spec 6.7）。
     */
    @Test
    void currentSummaryReadsLatestOrDefault() {
        when(summaryMapper.selectOne(any())).thenReturn(null);
        assertThat(service(memory(24, 40)).currentSummary(7L, 11L)).isEmpty();

        ChatSummary summary = new ChatSummary();
        summary.setContent("既有摘要");
        summary.setCoveredThroughSequence(5L);
        when(summaryMapper.selectOne(any())).thenReturn(summary);
        assertThat(service(memory(24, 40)).currentSummary(7L, 11L)).isEqualTo("既有摘要");
    }

    @Test
    void skipsWhenChatModelNotConfigured() {
        when(models.chatReady()).thenReturn(false);

        service(memory(1, 40)).summarizeIfNeeded(7L, 11L);

        verify(messageMapper, never()).selectList(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void summaryPromptContainsPriorSummaryAndNewMessages() {
        when(models.chatReady()).thenReturn(true);
        ChatSummary existing = new ChatSummary();
        existing.setContent("之前聊过退款");
        existing.setCoveredThroughSequence(2L);
        when(summaryMapper.selectOne(any())).thenReturn(existing);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(3L, "USER", "现在想改地址"), message(4L, "ASSISTANT", "可以")));
        when(models.chat(any(List.class))).thenReturn("合并后的摘要");

        service(memory(2, 40)).summarizeIfNeeded(7L, 11L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(models).chat(prompt.capture());
        String joined = prompt.getValue().toString();
        assertThat(joined).contains("之前聊过退款").contains("现在想改地址");
        verify(jdbcTemplate).update(anyString(), eq(7L), eq(11L), eq("合并后的摘要"), eq(4L));
    }
}
