package tech.liganex.studio.module.rag.index;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.module.ai.AiModelClient;
import tech.liganex.studio.module.rag.config.RagProperties;

import java.sql.Array;
import java.util.List;
import java.util.StringJoiner;

/** Project-owned pgvector + PostgreSQL full-text hybrid index. */
@Component
@RequiredArgsConstructor
public class PgVectorKnowledgeIndex implements KnowledgeIndex {
    private final JdbcTemplate jdbcTemplate;
    private final RagProperties properties;
    private final AiModelClient models;

    @Override
    @Transactional
    public void upsert(List<IndexChunk> chunks) {
        String parentSql = """
                INSERT INTO knowledge_chunk
                    (owner_user_id, knowledge_base_id, document_id, chunk_id, parent_chunk_id,
                     chunk_type, ordinal, content, parent_content, lexical_terms, embedding,
                     start_offset, end_offset, source_name, status, index_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, 'PARENT', ?, ?, ?, '', NULL, ?, ?, ?, 'READY', ?, now(), now())
                ON CONFLICT (chunk_id, owner_user_id, knowledge_base_id, document_id, index_version) DO UPDATE SET
                    content = EXCLUDED.content,
                    parent_content = EXCLUDED.parent_content,
                    start_offset = EXCLUDED.start_offset,
                    end_offset = EXCLUDED.end_offset,
                    source_name = EXCLUDED.source_name,
                    status = 'READY',
                    updated_at = now()
                """;
        chunks.stream().collect(java.util.stream.Collectors.toMap(IndexChunk::parentChunkId,
                        chunk -> chunk, (left, right) -> left, java.util.LinkedHashMap::new))
                .values().forEach(chunk -> jdbcTemplate.update(parentSql,
                        chunk.ownerUserId(), chunk.knowledgeBaseId(), chunk.documentId(), chunk.parentChunkId(),
                        chunk.ordinal(), chunk.parentContent(), chunk.parentContent(), chunk.startOffset(),
                        chunk.endOffset(), chunk.sourceName(), properties.getIndex().getVersion()));

        String sql = """
                INSERT INTO knowledge_chunk
                    (owner_user_id, knowledge_base_id, document_id, chunk_id, parent_chunk_id,
                     chunk_type, ordinal, content, parent_content, lexical_terms, embedding,
                     start_offset, end_offset, source_name, status, index_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'CHILD', ?, ?, ?, ?, CAST(? AS vector),
                        ?, ?, ?, 'READY', ?, now(), now())
                ON CONFLICT (chunk_id, owner_user_id, knowledge_base_id, document_id, index_version) DO UPDATE SET
                    content = EXCLUDED.content,
                    parent_content = EXCLUDED.parent_content,
                    lexical_terms = EXCLUDED.lexical_terms,
                    embedding = EXCLUDED.embedding,
                    start_offset = EXCLUDED.start_offset,
                    end_offset = EXCLUDED.end_offset,
                    source_name = EXCLUDED.source_name,
                    status = 'READY',
                    updated_at = now()
                """;
        for (IndexChunk chunk : chunks) {
            jdbcTemplate.update(sql,
                    chunk.ownerUserId(), chunk.knowledgeBaseId(), chunk.documentId(), chunk.chunkId(),
                    chunk.parentChunkId(), chunk.ordinal(), chunk.content(), chunk.parentContent(),
                    chunk.lexicalTerms(), vectorLiteral(chunk.embedding()),
                    chunk.startOffset(), chunk.endOffset(), chunk.sourceName(),
                    properties.getIndex().getVersion());
        }
    }

    @Override
    public List<IndexHit> search(SearchQuery query) {
        String sql = """
                WITH dense AS (
                    SELECT id, row_number() OVER (ORDER BY embedding <=> CAST(? AS vector)) AS pos,
                           1 - (embedding <=> CAST(? AS vector)) AS raw_score
                    FROM knowledge_chunk
                    WHERE owner_user_id = ? AND knowledge_base_id = ANY (?)
                      AND status = 'READY' AND chunk_type = 'CHILD'
                      AND index_version = ? AND embedding IS NOT NULL
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                ), lexical AS (
                    SELECT id, row_number() OVER (
                               ORDER BY ts_rank_cd(search_vector, plainto_tsquery('simple', ?)) DESC
                           ) AS pos,
                           ts_rank_cd(search_vector, plainto_tsquery('simple', ?)) AS raw_score
                    FROM knowledge_chunk
                    WHERE owner_user_id = ? AND knowledge_base_id = ANY (?)
                      AND status = 'READY' AND chunk_type = 'CHILD' AND index_version = ?
                      AND search_vector @@ plainto_tsquery('simple', ?)
                    ORDER BY raw_score DESC
                    LIMIT ?
                ), fused AS (
                    SELECT id, SUM(score) AS score FROM (
                        SELECT id, 1.0 / (? + pos) AS score FROM dense
                        UNION ALL
                        SELECT id, 1.0 / (? + pos) AS score FROM lexical
                    ) ranks GROUP BY id
                )
                SELECT row_number() OVER (ORDER BY fused.score DESC) AS rank,
                       c.knowledge_base_id, c.document_id, c.chunk_id, c.parent_chunk_id,
                       c.content, c.parent_content, c.source_name, c.start_offset, c.end_offset,
                       fused.score
                FROM fused JOIN knowledge_chunk c ON c.id = fused.id
                ORDER BY fused.score DESC, c.id
                LIMIT ?
                """;
        String vector = vectorLiteral(query.embedding());
        return jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement(sql);
            Array kbIds = connection.createArrayOf("bigint", query.knowledgeBaseIds().toArray(Long[]::new));
            int i = 1;
            statement.setString(i++, vector);
            statement.setString(i++, vector);
            statement.setLong(i++, query.ownerUserId());
            statement.setArray(i++, kbIds);
            statement.setString(i++, properties.getIndex().getVersion());
            statement.setString(i++, vector);
            statement.setInt(i++, query.candidateLimit());
            statement.setString(i++, query.lexicalTerms());
            statement.setString(i++, query.lexicalTerms());
            statement.setLong(i++, query.ownerUserId());
            statement.setArray(i++, kbIds);
            statement.setString(i++, properties.getIndex().getVersion());
            statement.setString(i++, query.lexicalTerms());
            statement.setInt(i++, query.candidateLimit());
            statement.setInt(i++, properties.getRetrieval().getRrfK());
            statement.setInt(i++, properties.getRetrieval().getRrfK());
            statement.setInt(i, query.finalLimit());
            return statement;
        }, (rs, rowNum) -> new IndexHit(
                rs.getInt("rank"), rs.getLong("knowledge_base_id"), rs.getLong("document_id"),
                rs.getString("chunk_id"), rs.getString("parent_chunk_id"), rs.getString("content"),
                rs.getString("parent_content"), rs.getString("source_name"),
                (Integer) rs.getObject("start_offset"), (Integer) rs.getObject("end_offset"),
                rs.getDouble("score")));
    }

    @Override
    @Transactional
    public void delete(IndexScope scope) {
        if (scope.documentId() != null) {
            jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE owner_user_id = ? AND document_id = ?",
                    scope.ownerUserId(), scope.documentId());
        } else {
            jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE owner_user_id = ? AND knowledge_base_id = ?",
                    scope.ownerUserId(), scope.knowledgeBaseId());
        }
    }

    @Override
    public IndexHealth health() {
        try {
            String version = jdbcTemplate.queryForObject(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
            return new IndexHealth(IndexBackend.PGVECTOR, version != null, models.dimensions(),
                    version == null ? "pgvector extension missing" : "pgvector " + version);
        } catch (Exception ex) {
            return new IndexHealth(IndexBackend.PGVECTOR, false, models.dimensions(), "pgvector unavailable");
        }
    }

    static String vectorLiteral(float[] embedding) {
        StringJoiner value = new StringJoiner(",", "[", "]");
        for (float component : embedding) {
            if (!Float.isFinite(component)) {
                throw new IllegalArgumentException("embedding contains a non-finite value");
            }
            value.add(Float.toString(component));
        }
        return value.toString();
    }
}
