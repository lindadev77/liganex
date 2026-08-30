package tech.liganex.studio.module.rag.index;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.rag.config.RagProperties;

@Service
@RequiredArgsConstructor
public class IndexRebuildService {
    private final JdbcTemplate jdbcTemplate;
    private final RagProperties properties;

    @Transactional
    public int rebuild(Long ownerUserId, Long knowledgeBaseId) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM knowledge_base WHERE id=? AND owner_user_id=? AND status='ACTIVE'",
                Integer.class, knowledgeBaseId, ownerUserId);
        if (exists == null || exists != 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        jdbcTemplate.update("""
                UPDATE knowledge_document SET status='PENDING', progress=0, error_summary=NULL, updated_at=now()
                WHERE owner_user_id=? AND knowledge_base_id=? AND status <> 'DELETING'
                """, ownerUserId, knowledgeBaseId);
        return jdbcTemplate.update("""
                INSERT INTO knowledge_index_job
                    (owner_user_id, knowledge_base_id, document_id, job_type, idempotency_key,
                     status, progress, retry_count, max_retries, next_retry_at, created_at, updated_at)
                SELECT owner_user_id, knowledge_base_id, id, 'REINDEX',
                       'reindex:' || id || ':' || ?, 'PENDING', 0, 0, ?, now(), now(), now()
                FROM knowledge_document
                WHERE owner_user_id=? AND knowledge_base_id=? AND status <> 'DELETING'
                ON CONFLICT (owner_user_id, idempotency_key) DO UPDATE SET
                    status='PENDING', progress=0, retry_count=0, next_retry_at=now(),
                    locked_by=NULL, locked_at=NULL, error_summary=NULL, completed_at=NULL, updated_at=now()
                """, properties.getIndex().getVersion(), properties.getWorker().getMaxAttempts(),
                ownerUserId, knowledgeBaseId);
    }
}
