package tech.liganex.studio.module.rag.index;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.liganex.studio.module.ai.AiModelClient;
import tech.liganex.studio.module.ai.SensitiveValueSanitizer;
import tech.liganex.studio.module.rag.config.RagProperties;
import tech.liganex.studio.module.rag.splitter.ParentChildSplitter;
import tech.liganex.studio.module.rag.text.HybridTermTokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexWorker {
    private static final String WORKER_ID = "studio-" + UUID.randomUUID();

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeIndex index;
    private final AiModelClient models;
    private final HybridTermTokenizer tokenizer;
    private final RagProperties properties;

    @Scheduled(fixedDelayString = "${liganex.rag.worker.poll-delay-ms:2000}")
    public void poll() {
        if (!properties.getWorker().isEnabled() || !models.embeddingReady()) {
            return;
        }
        recoverStaleJobs();
        Job job = claim();
        if (job == null) {
            return;
        }
        try {
            process(job);
        } catch (Exception ex) {
            fail(job, ex);
        }
    }

    private Job claim() {
        return jdbcTemplate.query("""
                WITH candidate AS (
                    SELECT id FROM knowledge_index_job
                    WHERE status = 'PENDING' AND next_retry_at <= now()
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE knowledge_index_job job
                SET status = 'PROCESSING', locked_by = ?, locked_at = now(), updated_at = now()
                FROM candidate WHERE job.id = candidate.id
                RETURNING job.id, job.owner_user_id, job.knowledge_base_id, job.document_id,
                          job.retry_count, job.max_retries
                """, rs -> rs.next() ? new Job(rs.getLong("id"), rs.getLong("owner_user_id"),
                rs.getLong("knowledge_base_id"), rs.getLong("document_id"),
                rs.getInt("retry_count"), rs.getInt("max_retries")) : null, WORKER_ID);
    }

    private void process(Job job) throws Exception {
        DocumentSource source = jdbcTemplate.query("""
                SELECT d.title, d.media_type, d.extracted_text, b.content
                FROM knowledge_document d
                LEFT JOIN knowledge_document_blob b
                  ON b.document_id = d.id AND b.owner_user_id = d.owner_user_id
                WHERE d.id = ? AND d.owner_user_id = ? AND d.knowledge_base_id = ?
                  AND d.status <> 'DELETING'
                """, rs -> rs.next() ? new DocumentSource(rs.getString("title"), rs.getString("media_type"),
                rs.getString("extracted_text"), rs.getBytes("content")) : null,
                job.documentId(), job.ownerUserId(), job.knowledgeBaseId());
        if (source == null) {
            jdbcTemplate.update("UPDATE knowledge_index_job SET status='CANCELLED', completed_at=now(), updated_at=now() WHERE id=?",
                    job.id());
            return;
        }
        String text = extract(source);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文档没有可索引文本");
        }
        jdbcTemplate.update("""
                UPDATE knowledge_document SET status='PROCESSING', progress=20, extracted_text=?, updated_at=now()
                WHERE id=? AND owner_user_id=?
                """, text, job.documentId(), job.ownerUserId());

        ParentChildSplitter splitter = new ParentChildSplitter(properties.getChunk().getParentSize(),
                properties.getChunk().getChildSize(), properties.getChunk().getChildOverlap());
        List<ParentChildSplitter.Chunk> chunks = splitter.split(job.documentId(), text);
        List<float[]> embeddings = models.embed(chunks.stream().map(ParentChildSplitter.Chunk::content).toList());
        List<KnowledgeIndex.IndexChunk> indexed = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ParentChildSplitter.Chunk chunk = chunks.get(i);
            indexed.add(new KnowledgeIndex.IndexChunk(job.ownerUserId(), job.knowledgeBaseId(), job.documentId(),
                    chunk.chunkId(), chunk.parentChunkId(), chunk.ordinal(), chunk.content(), chunk.parentContent(),
                    tokenizer.terms(chunk.content()), embeddings.get(i), chunk.startOffset(), chunk.endOffset(), source.title()));
        }
        jdbcTemplate.update("UPDATE knowledge_document SET progress=70, updated_at=now() WHERE id=? AND owner_user_id=?",
                job.documentId(), job.ownerUserId());
        index.upsert(indexed);
        jdbcTemplate.update("""
                INSERT INTO knowledge_document_index
                    (owner_user_id, knowledge_base_id, document_id, backend, embedding_model,
                     dimensions, index_version, status, chunk_count, created_at, updated_at, ready_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'READY', ?, now(), now(), now())
                ON CONFLICT (owner_user_id, document_id, backend, embedding_model, index_version)
                DO UPDATE SET status='READY', chunk_count=EXCLUDED.chunk_count, error_summary=NULL,
                              updated_at=now(), ready_at=now()
                """, job.ownerUserId(), job.knowledgeBaseId(), job.documentId(),
                properties.getIndex().getBackend().name(), models.embeddingModelName(), models.dimensions(),
                properties.getIndex().getVersion(), indexed.size());
        jdbcTemplate.update("""
                UPDATE knowledge_document SET status='READY', progress=100, chunk_count=?, index_version=?,
                       error_summary=NULL, indexed_at=now(), updated_at=now()
                WHERE id=? AND owner_user_id=?
                """, indexed.size(), properties.getIndex().getVersion(), job.documentId(), job.ownerUserId());
        jdbcTemplate.update("""
                UPDATE knowledge_index_job SET status='SUCCEEDED', progress=100, locked_by=NULL, locked_at=NULL,
                       error_summary=NULL, completed_at=now(), updated_at=now() WHERE id=?
                """, job.id());
    }

    private String extract(DocumentSource source) throws Exception {
        if (source.extractedText() != null && !source.extractedText().isBlank()) {
            return source.extractedText();
        }
        if (!"application/pdf".equals(source.mediaType()) || source.bytes() == null) {
            return source.extractedText();
        }
        try (var document = Loader.loadPDF(source.bytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private void fail(Job job, Exception error) {
        String detail = SensitiveValueSanitizer.sanitize(error.getMessage());
        if (detail == null || detail.isBlank()) {
            detail = "文档处理失败";
        }
        detail = detail.length() > 900 ? detail.substring(0, 900) : detail;
        int retries = job.retryCount() + 1;
        if (retries >= job.maxRetries()) {
            jdbcTemplate.update("""
                    UPDATE knowledge_index_job SET status='FAILED', retry_count=?, error_summary=?,
                           locked_by=NULL, locked_at=NULL, completed_at=now(), updated_at=now() WHERE id=?
                    """, retries, detail, job.id());
            jdbcTemplate.update("""
                    UPDATE knowledge_document SET status='FAILED', error_summary=?, updated_at=now()
                    WHERE id=? AND owner_user_id=?
                    """, detail, job.documentId(), job.ownerUserId());
        } else {
            long delaySeconds = Math.min(300, 1L << Math.min(retries, 8));
            jdbcTemplate.update("""
                    UPDATE knowledge_index_job SET status='PENDING', retry_count=?, error_summary=?,
                           next_retry_at=now() + (? * interval '1 second'), locked_by=NULL, locked_at=NULL,
                           updated_at=now() WHERE id=?
                    """, retries, detail, delaySeconds, job.id());
        }
        log.warn("knowledge indexing failed job={} attempt={}", job.id(), retries);
    }

    private void recoverStaleJobs() {
        jdbcTemplate.update("""
                UPDATE knowledge_index_job SET status='PENDING', locked_by=NULL, locked_at=NULL,
                       next_retry_at=now(), updated_at=now()
                WHERE status='PROCESSING' AND locked_at < now() - interval '10 minutes'
                """);
    }

    record Job(long id, long ownerUserId, long knowledgeBaseId, long documentId,
               int retryCount, int maxRetries) {
    }

    record DocumentSource(String title, String mediaType, String extractedText, byte[] bytes) {
    }
}
