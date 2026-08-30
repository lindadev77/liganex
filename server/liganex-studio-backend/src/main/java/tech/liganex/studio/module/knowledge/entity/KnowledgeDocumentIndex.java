package tech.liganex.studio.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("knowledge_document_index")
public class KnowledgeDocumentIndex {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long knowledgeBaseId;
    private Long documentId;
    private String backend;
    private String embeddingModel;
    private Integer dimensions;
    private String indexVersion;
    private String status;
    private Integer chunkCount;
    private String errorSummary;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant readyAt;
}
