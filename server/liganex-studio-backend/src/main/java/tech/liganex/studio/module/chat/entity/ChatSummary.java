package tech.liganex.studio.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("chat_summary")
public class ChatSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long conversationId;
    private String content;
    private Long coveredThroughSequence;
    private Instant createdAt;
    private Instant updatedAt;
}
