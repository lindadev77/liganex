package tech.liganex.studio.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tech.liganex.studio.module.knowledge.entity.KnowledgeIndexJob;

public interface KnowledgeIndexJobMapper extends BaseMapper<KnowledgeIndexJob> {

    @Select("""
            SELECT * FROM knowledge_index_job
            WHERE owner_user_id = #{ownerUserId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND document_id = #{documentId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    KnowledgeIndexJob selectLatestOwnedForDocument(
            @Param("ownerUserId") Long ownerUserId,
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("documentId") Long documentId);
}
