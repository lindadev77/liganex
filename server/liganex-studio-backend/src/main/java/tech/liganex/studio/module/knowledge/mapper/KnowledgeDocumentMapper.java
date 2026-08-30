package tech.liganex.studio.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tech.liganex.studio.module.knowledge.entity.KnowledgeDocument;

import java.util.List;

public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("""
            SELECT * FROM knowledge_document
            WHERE owner_user_id = #{ownerUserId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND id = #{documentId}
              AND status <> 'DELETING'
            """)
    KnowledgeDocument selectOwnedById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("documentId") Long documentId);

    @Select("""
            SELECT * FROM knowledge_document
            WHERE owner_user_id = #{ownerUserId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND status <> 'DELETING'
            ORDER BY created_at DESC, id DESC
            """)
    List<KnowledgeDocument> selectAllOwnedInBase(
            @Param("ownerUserId") Long ownerUserId,
            @Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("""
            SELECT * FROM knowledge_document
            WHERE owner_user_id = #{ownerUserId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND content_sha256 = #{contentSha256}
              AND status <> 'DELETING'
            LIMIT 1
            """)
    KnowledgeDocument selectOwnedByHash(
            @Param("ownerUserId") Long ownerUserId,
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("contentSha256") String contentSha256);
}
