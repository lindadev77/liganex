package tech.liganex.studio.module.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.liganex.studio.module.ai.config.ChatProperties;
import tech.liganex.studio.module.ai.config.EmbeddingProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Lazy, manually wired OpenAI-compatible model clients. Credentials remain in process memory only. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelClient {
    private final EmbeddingProperties embeddingProperties;
    private final ChatProperties chatProperties;

    private volatile EmbeddingModel embeddingModel;
    private volatile ChatModel chatModel;
    private volatile StreamingChatModel streamingChatModel;

    public boolean embeddingReady() {
        return embeddingProperties.configured();
    }

    public boolean chatReady() {
        return chatProperties.configured();
    }

    public int dimensions() {
        return embeddingProperties.getDimensions();
    }

    public String embeddingModelName() {
        return embeddingProperties.getModelName();
    }

    public List<float[]> embed(List<String> texts) {
        requireEmbedding();
        try {
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (String text : texts) {
                float[] vector = embeddingModel().embed(TextSegment.from(text)).content().vector();
                if (vector.length != dimensions()) {
                    throw new AiProviderException("Embedding 向量维度与配置不一致");
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("embedding provider call failed: {}", SensitiveValueSanitizer.sanitize(ex.getMessage()));
            throw new AiProviderException("Embedding 服务暂不可用", ex);
        }
    }

    public String chat(List<ChatMessage> messages) {
        requireChat();
        try {
            return chatModel().chat(messages).aiMessage().text();
        } catch (Exception ex) {
            log.warn("chat provider call failed: {}", SensitiveValueSanitizer.sanitize(ex.getMessage()));
            throw new AiProviderException("问答模型服务暂不可用", ex);
        }
    }

    public void stream(List<ChatMessage> messages, StreamHandler handler) {
        requireChat();
        try {
            streamingChatModel().chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    handler.onToken(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    handler.onComplete();
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("streaming chat provider failed: {}",
                            SensitiveValueSanitizer.sanitize(error.getMessage()));
                    handler.onError(new AiProviderException("问答模型服务暂不可用", error));
                }
            });
        } catch (Exception ex) {
            handler.onError(new AiProviderException("问答模型服务暂不可用", ex));
        }
    }

    private void requireEmbedding() {
        if (!embeddingReady()) {
            throw new AiProviderException("Embedding 模型尚未配置");
        }
    }

    private void requireChat() {
        if (!chatReady()) {
            throw new AiProviderException("问答模型尚未配置");
        }
    }

    private EmbeddingModel embeddingModel() {
        if (embeddingModel == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    embeddingModel = OpenAiEmbeddingModel.builder()
                            .baseUrl(embeddingProperties.getBaseUrl())
                            .apiKey(embeddingProperties.getApiKey())
                            .modelName(embeddingProperties.getModelName())
                            .dimensions(embeddingProperties.getDimensions())
                            .timeout(Duration.ofMillis(embeddingProperties.getTimeoutMs()))
                            .maxRetries(embeddingProperties.getMaxRetries())
                            .build();
                }
            }
        }
        return embeddingModel;
    }

    private ChatModel chatModel() {
        if (chatModel == null) {
            synchronized (this) {
                if (chatModel == null) {
                    chatModel = OpenAiChatModel.builder()
                            .baseUrl(chatProperties.getBaseUrl())
                            .apiKey(chatProperties.getApiKey())
                            .modelName(chatProperties.getModelName())
                            .timeout(Duration.ofMillis(chatProperties.getTimeoutMs()))
                            .maxRetries(chatProperties.getMaxRetries())
                            .build();
                }
            }
        }
        return chatModel;
    }

    private StreamingChatModel streamingChatModel() {
        if (streamingChatModel == null) {
            synchronized (this) {
                if (streamingChatModel == null) {
                    streamingChatModel = OpenAiStreamingChatModel.builder()
                            .baseUrl(chatProperties.getBaseUrl())
                            .apiKey(chatProperties.getApiKey())
                            .modelName(chatProperties.getModelName())
                            .timeout(Duration.ofMillis(chatProperties.getTimeoutMs()))
                            .build();
                }
            }
        }
        return streamingChatModel;
    }

    public interface StreamHandler {
        void onToken(String token);

        void onComplete();

        void onError(Throwable error);
    }
}
