package tech.liganex.studio.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long knowledgeBaseId;
    private String title;
    private String sourceType;
    private String mediaType;
    private String originalFilename;
    private Long sizeBytes;
    private String contentSha256;
    private String extractedText;
    private String status;
    private Integer progress;
    private Integer chunkCount;
    private String indexVersion;
    private String errorSummary;
    private Instant indexedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
