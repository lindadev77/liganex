package tech.liganex.studio.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tech.liganex.studio.module.knowledge.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("""
            SELECT * FROM knowledge_base
            WHERE owner_user_id = #{ownerUserId} AND id = #{id} AND status = 'ACTIVE'
            """)
    KnowledgeBase selectOwnedById(@Param("ownerUserId") Long ownerUserId, @Param("id") Long id);

    @Select("""
            SELECT * FROM knowledge_base
            WHERE owner_user_id = #{ownerUserId} AND status = 'ACTIVE'
            ORDER BY updated_at DESC, id DESC
            """)
    List<KnowledgeBase> selectAllOwned(@Param("ownerUserId") Long ownerUserId);
}
