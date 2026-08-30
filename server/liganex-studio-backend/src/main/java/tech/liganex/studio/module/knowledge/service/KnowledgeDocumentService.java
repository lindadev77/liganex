package tech.liganex.studio.module.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.knowledge.config.KnowledgeUploadProperties;
import tech.liganex.studio.module.knowledge.dto.CreateTextDocumentRequest;
import tech.liganex.studio.module.knowledge.dto.KnowledgeDocumentResponse;
import tech.liganex.studio.module.knowledge.entity.KnowledgeDocument;
import tech.liganex.studio.module.knowledge.entity.KnowledgeDocumentBlob;
import tech.liganex.studio.module.knowledge.entity.KnowledgeIndexJob;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeDocumentBlobMapper;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeDocumentMapper;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeIndexJobMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService {

    private static final int DEFAULT_MAX_RETRIES = 4;

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentBlobMapper knowledgeDocumentBlobMapper;
    private final KnowledgeIndexJobMapper knowledgeIndexJobMapper;
    private final KnowledgeFileValidator fileValidator;
    private final KnowledgeUploadProperties uploadProperties;

    @Transactional
    public KnowledgeDocumentResponse createText(
            Long ownerUserId,
            Long knowledgeBaseId,
            CreateTextDocumentRequest request) {
        knowledgeBaseService.requireOwned(ownerUserId, knowledgeBaseId);
        byte[] bytes = request.content().getBytes(StandardCharsets.UTF_8);
        if (request.content().isBlank() || bytes.length == 0) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_EMPTY);
        }
        if (bytes.length > uploadProperties.getMaxBytes()) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TOO_LARGE);
        }
        return persist(
                ownerUserId,
                knowledgeBaseId,
                request.title().trim(),
                "TEXT",
                "text/plain",
                null,
                bytes,
                request.content());
    }

    @Transactional
    public KnowledgeDocumentResponse upload(
            Long ownerUserId,
            Long knowledgeBaseId,
            String requestedTitle,
            MultipartFile file) {
        knowledgeBaseService.requireOwned(ownerUserId, knowledgeBaseId);
        KnowledgeFileValidator.ValidatedFile validated = fileValidator.validate(file);
        String title = requestedTitle == null || requestedTitle.isBlank()
                ? validated.filename()
                : requestedTitle.trim();
        if (title.length() > 255) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文档标题最长 255 字符");
        }
        return persist(
                ownerUserId,
                knowledgeBaseId,
                title,
                "FILE",
                validated.mediaType(),
                validated.filename(),
                validated.bytes(),
                validated.extractedText());
    }

    public List<KnowledgeDocumentResponse> list(Long ownerUserId, Long knowledgeBaseId) {
        knowledgeBaseService.requireOwned(ownerUserId, knowledgeBaseId);
        return knowledgeDocumentMapper.selectAllOwnedInBase(ownerUserId, knowledgeBaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    public KnowledgeDocumentResponse get(Long ownerUserId, Long knowledgeBaseId, Long documentId) {
        knowledgeBaseService.requireOwned(ownerUserId, knowledgeBaseId);
        return toResponse(requireOwned(ownerUserId, knowledgeBaseId, documentId));
    }

    @Transactional
    public KnowledgeDocumentResponse retry(Long ownerUserId, Long knowledgeBaseId, Long documentId) {
        knowledgeBaseService.requireOwned(ownerUserId, knowledgeBaseId);
        KnowledgeDocument document = requireOwned(ownerUserId, knowledgeBaseId, documentId);
        if (!"FAILED".equals(document.getStatus())) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_RETRY_NOT_ALLOWED);
        }

        KnowledgeIndexJob job = knowledgeIndexJobMapper.selectLatestOwnedForDocument(
                ownerUserId, knowledgeBaseId, documentId);
        if (job == null) {
            job = newIndexJob(document, "REINDEX");
            knowledgeIndexJobMapper.insert(job);
        } else {
            job.setStatus("PENDING");
            job.setProgress(0);
            job.setRetryCount(0);
            job.setNextRetryAt(Instant.now());
            job.setLockedBy(null);
            job.setLockedAt(null);
            job.setErrorSummary(null);
            job.setCompletedAt(null);
            job.setUpdatedAt(Instant.now());
            knowledgeIndexJobMapper.updateById(job);
        }

        document.setStatus("PENDING");
        document.setProgress(0);
        document.setErrorSummary(null);
        document.setUpdatedAt(Instant.now());
        knowledgeDocumentMapper.updateById(document);
        return toResponse(document);
    }

    @Transactional
    public void delete(Long ownerUserId, Long knowledgeBaseId, Long documentId) {
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getOwnerUserId, ownerUserId)
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocument::getId, documentId));
    }

    private KnowledgeDocumentResponse persist(
            Long ownerUserId,
            Long knowledgeBaseId,
            String title,
            String sourceType,
            String mediaType,
            String originalFilename,
            byte[] bytes,
            String extractedText) {
        String sha256 = sha256(bytes);
        if (knowledgeDocumentMapper.selectOwnedByHash(ownerUserId, knowledgeBaseId, sha256) != null) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_ALREADY_EXISTS);
        }

        Instant now = Instant.now();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setOwnerUserId(ownerUserId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setTitle(title);
        document.setSourceType(sourceType);
        document.setMediaType(mediaType);
        document.setOriginalFilename(originalFilename);
        document.setSizeBytes((long) bytes.length);
        document.setContentSha256(sha256);
        document.setExtractedText(extractedText);
        document.setStatus("PENDING");
        document.setProgress(0);
        document.setChunkCount(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        try {
            knowledgeDocumentMapper.insert(document);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_ALREADY_EXISTS);
        }

        KnowledgeDocumentBlob blob = new KnowledgeDocumentBlob();
        blob.setDocumentId(document.getId());
        blob.setOwnerUserId(ownerUserId);
        blob.setKnowledgeBaseId(knowledgeBaseId);
        blob.setContent(bytes);
        blob.setSizeBytes((long) bytes.length);
        blob.setContentSha256(sha256);
        blob.setCreatedAt(now);
        knowledgeDocumentBlobMapper.insert(blob);

        knowledgeIndexJobMapper.insert(newIndexJob(document, "INDEX"));
        return toResponse(document);
    }

    private KnowledgeIndexJob newIndexJob(KnowledgeDocument document, String jobType) {
        Instant now = Instant.now();
        KnowledgeIndexJob job = new KnowledgeIndexJob();
        job.setOwnerUserId(document.getOwnerUserId());
        job.setKnowledgeBaseId(document.getKnowledgeBaseId());
        job.setDocumentId(document.getId());
        job.setJobType(jobType);
        job.setIdempotencyKey("index:" + document.getId() + ":" + document.getContentSha256());
        job.setStatus("PENDING");
        job.setProgress(0);
        job.setRetryCount(0);
        job.setMaxRetries(DEFAULT_MAX_RETRIES);
        job.setNextRetryAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }

    private KnowledgeDocument requireOwned(Long ownerUserId, Long knowledgeBaseId, Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectOwnedById(
                ownerUserId, knowledgeBaseId, documentId);
        if (document == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument document) {
        return KnowledgeDocumentResponse.from(
                document,
                KnowledgeErrorSanitizer.sanitize(document.getErrorSummary()));
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
