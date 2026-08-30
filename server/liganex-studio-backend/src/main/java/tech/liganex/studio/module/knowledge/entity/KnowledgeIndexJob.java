package tech.liganex.studio.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("knowledge_index_job")
public class KnowledgeIndexJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long knowledgeBaseId;
    private Long documentId;
    private String jobType;
    private String idempotencyKey;
    private String status;
    private Integer progress;
    private Integer retryCount;
    private Integer maxRetries;
    private Instant nextRetryAt;
    private String lockedBy;
    private Instant lockedAt;
    private String errorSummary;
    private String payload;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
