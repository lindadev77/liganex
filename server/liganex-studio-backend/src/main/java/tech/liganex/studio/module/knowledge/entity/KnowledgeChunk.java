package tech.liganex.studio.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String chunkId;
    private Long ownerUserId;
    private Long knowledgeBaseId;
    private Long documentId;
    private String parentChunkId;
    private String chunkType;
    private Integer ordinal;
    private String indexVersion;
    private String content;
    private String parentContent;
    private String lexicalTerms;
    @TableField(exist = false)
    private String embedding;
    private String sourceName;
    private String status;
    private Integer pageNumber;
    private Integer startOffset;
    private Integer endOffset;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
