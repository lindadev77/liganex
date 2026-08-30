package tech.liganex.studio.module.knowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.knowledge.config.KnowledgeUploadProperties;
import tech.liganex.studio.module.knowledge.dto.CreateTextDocumentRequest;
import tech.liganex.studio.module.knowledge.entity.KnowledgeDocument;
import tech.liganex.studio.module.knowledge.entity.KnowledgeIndexJob;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeDocumentBlobMapper;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeDocumentMapper;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeIndexJobMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private KnowledgeDocumentMapper documentMapper;
    @Mock
    private KnowledgeDocumentBlobMapper blobMapper;
    @Mock
    private KnowledgeIndexJobMapper jobMapper;
    @Mock
    private KnowledgeFileValidator fileValidator;

    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp() {
        KnowledgeUploadProperties properties = new KnowledgeUploadProperties();
        properties.setMaxBytes(1024 * 1024);
        service = new KnowledgeDocumentService(
                knowledgeBaseService, documentMapper, blobMapper, jobMapper, fileValidator, properties);
    }

    @Test
    void textInputPersistsOwnerScopedDocumentBlobAndDurableJob() {
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(88L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));

        service.createText(42L, 7L, new CreateTextDocumentRequest("说明", "可检索内容"));

        ArgumentCaptor<KnowledgeDocument> document = ArgumentCaptor.forClass(KnowledgeDocument.class);
        ArgumentCaptor<KnowledgeIndexJob> job = ArgumentCaptor.forClass(KnowledgeIndexJob.class);
        verify(documentMapper).insert(document.capture());
        verify(jobMapper).insert(job.capture());
        assertThat(document.getValue().getOwnerUserId()).isEqualTo(42L);
        assertThat(document.getValue().getKnowledgeBaseId()).isEqualTo(7L);
        assertThat(document.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(job.getValue().getOwnerUserId()).isEqualTo(42L);
        assertThat(job.getValue().getDocumentId()).isEqualTo(88L);
        assertThat(job.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void retryOnlyAllowsFailedOwnedDocument() {
        KnowledgeDocument ready = new KnowledgeDocument();
        ready.setId(88L);
        ready.setOwnerUserId(42L);
        ready.setKnowledgeBaseId(7L);
        ready.setStatus("READY");
        when(documentMapper.selectOwnedById(42L, 7L, 88L)).thenReturn(ready);

        assertThatThrownBy(() -> service.retry(42L, 7L, 88L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.KNOWLEDGE_DOCUMENT_RETRY_NOT_ALLOWED));
    }
}
