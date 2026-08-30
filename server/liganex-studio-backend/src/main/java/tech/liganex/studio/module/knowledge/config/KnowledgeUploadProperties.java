package tech.liganex.studio.module.knowledge.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "liganex.rag.upload")
public class KnowledgeUploadProperties {

    @Min(1)
    private long maxBytes = 10L * 1024 * 1024;

    @Min(1)
    private int maxPdfPages = 200;
}
