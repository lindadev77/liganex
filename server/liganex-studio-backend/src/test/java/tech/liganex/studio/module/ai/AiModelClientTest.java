package tech.liganex.studio.module.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.liganex.studio.module.ai.config.ChatProperties;
import tech.liganex.studio.module.ai.config.EmbeddingProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void callsOpenAiCompatibleEmbeddingAndChatEndpoints() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> json(exchange, 200, """
                {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}],
                 "model":"stub-embedding","usage":{"prompt_tokens":1,"total_tokens":1}}
                """));
        server.createContext("/v1/chat/completions", exchange -> json(exchange, 200, """
                {"id":"test","object":"chat.completion","created":1,"model":"stub-chat",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"测试回答"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """));
        server.start();
        AiModelClient client = client(3);

        assertThat(client.embed(List.of("hello")).getFirst()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(client.chat(List.of(UserMessage.from("hello")))).isEqualTo("测试回答");
    }

    @Test
    void rejectsUnexpectedEmbeddingDimension() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> json(exchange, 200, """
                {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                 "model":"stub","usage":{"prompt_tokens":1,"total_tokens":1}}
                """));
        server.start();

        assertThatThrownBy(() -> client(3).embed(List.of("hello")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("维度");
    }

    @Test
    void mapsProviderErrorsToGenericPublicMessage() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> json(exchange, 500, """
                {"error":{"message":"Authorization: Bearer provider-secret"}}
                """));
        server.start();

        assertThatThrownBy(() -> client(3).embed(List.of("hello")))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("Embedding 服务暂不可用")
                .hasMessageNotContaining("provider-secret");
    }

    private AiModelClient client(int dimensions) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setBaseUrl(baseUrl);
        embedding.setApiKey("test-only-key");
        embedding.setModelName("stub-embedding");
        embedding.setDimensions(dimensions);
        embedding.setMaxRetries(0);
        ChatProperties chat = new ChatProperties();
        chat.setBaseUrl(baseUrl);
        chat.setApiKey("test-only-key");
        chat.setModelName("stub-chat");
        chat.setMaxRetries(0);
        return new AiModelClient(embedding, chat);
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
