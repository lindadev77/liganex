package tech.liganex.studio.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long conversationId;
    private Long sequence;
    private String role;
    private String content;
    private String status;
    @TableField("citations")
    private String citations;
    private Instant createdAt;
    private Instant completedAt;
}
