package tech.liganex.studio.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "liganex.ai.chat")
public class ChatProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private long timeoutMs = 120_000;
    private int maxRetries = 2;

    public boolean configured() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(modelName);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
