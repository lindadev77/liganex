package tech.liganex.studio.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "liganex.ai.embedding")
public class EmbeddingProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private int dimensions = 1536;
    private long timeoutMs = 60_000;
    private int maxRetries = 2;

    public boolean configured() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(modelName) && dimensions > 0;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
