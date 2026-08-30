package tech.liganex.studio.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String title;
    private String status;
    private Long nextMessageSequence;
    private Instant createdAt;
    private Instant updatedAt;
}
