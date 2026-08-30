package tech.liganex.studio.module.knowledge.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.knowledge.config.KnowledgeUploadProperties;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeFileValidatorTest {

    private final KnowledgeUploadProperties properties = properties();
    private final KnowledgeFileValidator validator = new KnowledgeFileValidator(properties);

    @Test
    void acceptsUtf8MarkdownAndExtractsText() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.md", "text/markdown", "# 使用说明".getBytes(StandardCharsets.UTF_8));

        KnowledgeFileValidator.ValidatedFile result = validator.validate(file);

        assertThat(result.filename()).isEqualTo("guide.md");
        assertThat(result.extractedText()).isEqualTo("# 使用说明");
    }

    @Test
    void rejectsTraversalFilenameAndMimeMismatch() {
        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../guide.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", "not-pdf".getBytes(StandardCharsets.UTF_8));

        assertError(traversal, ErrorCode.KNOWLEDGE_DOCUMENT_FILENAME_INVALID);
        assertError(fakePdf, ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED);
    }

    @Test
    void acceptsNonEncryptedPdfWithinPageLimit() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", pdf);

        KnowledgeFileValidator.ValidatedFile result = validator.validate(file);

        assertThat(result.mediaType()).isEqualTo("application/pdf");
        assertThat(result.extractedText()).isNull();
    }

    @Test
    void rejectsOversizedDocumentBeforeReadingContent() {
        properties.setMaxBytes(3);
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", "text/plain", "four".getBytes(StandardCharsets.UTF_8));

        assertError(file, ErrorCode.KNOWLEDGE_DOCUMENT_TOO_LARGE);
    }

    private void assertError(MockMultipartFile file, ErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private KnowledgeUploadProperties properties() {
        KnowledgeUploadProperties value = new KnowledgeUploadProperties();
        value.setMaxBytes(1024 * 1024);
        value.setMaxPdfPages(5);
        return value;
    }
}
