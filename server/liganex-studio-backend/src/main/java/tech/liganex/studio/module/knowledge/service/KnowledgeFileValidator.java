package tech.liganex.studio.module.knowledge.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.knowledge.config.KnowledgeUploadProperties;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class KnowledgeFileValidator {

    private static final Set<String> TEXT_TYPES = Set.of(
            "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream");

    private final KnowledgeUploadProperties properties;

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_EMPTY);
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TOO_LARGE);
        }

        String filename = validateFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        String mediaType = normalizeMediaType(file.getContentType());
        byte[] bytes = readBytes(file);
        if (bytes.length == 0) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_EMPTY);
        }
        if (bytes.length > properties.getMaxBytes()) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TOO_LARGE);
        }

        return switch (extension) {
            case "txt", "md", "markdown" -> validateText(filename, mediaType, bytes);
            case "pdf" -> validatePdf(filename, mediaType, bytes);
            default -> throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED);
        };
    }

    private ValidatedFile validateText(String filename, String mediaType, byte[] bytes) {
        if (!TEXT_TYPES.contains(mediaType)) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED);
        }
        if (containsNullByte(bytes)) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED);
        }
        if (text.isBlank()) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_EMPTY);
        }
        return new ValidatedFile(filename, mediaType, bytes, text);
    }

    private ValidatedFile validatePdf(String filename, String mediaType, byte[] bytes) {
        if (!"application/pdf".equals(mediaType)
                || bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED);
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()
                    || document.getNumberOfPages() == 0
                    || document.getNumberOfPages() > properties.getMaxPdfPages()) {
                throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED,
                        "PDF 已加密、没有页面或页数超过限制");
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED, "PDF 文件格式无效");
        }
        return new ValidatedFile(filename, mediaType, bytes, null);
    }

    private String validateFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()
                || originalFilename.length() > 255
                || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_FILENAME_INVALID);
        }
        for (int i = 0; i < originalFilename.length(); i++) {
            if (Character.isISOControl(originalFilename.charAt(i))) {
                throw new BizException(ErrorCode.KNOWLEDGE_DOCUMENT_FILENAME_INVALID);
            }
        }
        return originalFilename.trim();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        int parameter = contentType.indexOf(';');
        String value = parameter < 0 ? contentType : contentType.substring(0, parameter);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "无法读取上传文件");
        }
    }

    private boolean containsNullByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    public record ValidatedFile(
            String filename,
            String mediaType,
            byte[] bytes,
            String extractedText) {
    }
}
