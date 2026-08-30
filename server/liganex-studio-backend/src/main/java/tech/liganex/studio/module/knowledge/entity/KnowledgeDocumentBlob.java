package tech.liganex.studio.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("knowledge_document_blob")
public class KnowledgeDocumentBlob {

    @TableId(type = IdType.INPUT)
    private Long documentId;
    private Long ownerUserId;
    private Long knowledgeBaseId;
    private byte[] content;
    private Long sizeBytes;
    private String contentSha256;
    private Instant createdAt;
}
