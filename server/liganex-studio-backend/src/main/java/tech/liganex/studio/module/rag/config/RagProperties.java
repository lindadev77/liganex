package tech.liganex.studio.module.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import tech.liganex.studio.module.rag.index.IndexBackend;

@Data
@ConfigurationProperties(prefix = "liganex.rag")
public class RagProperties {
    private Index index = new Index();
    private Retrieval retrieval = new Retrieval();
    private Chunk chunk = new Chunk();
    private Worker worker = new Worker();
    private Upload upload = new Upload();
    private Memory memory = new Memory();

    @Data public static class Index { private IndexBackend backend = IndexBackend.PGVECTOR; private String version = "v1"; }
    @Data public static class Retrieval { private int candidateLimit = 20; private int finalLimit = 6; private int rrfK = 60; }
    @Data public static class Chunk { private int parentSize = 1800; private int childSize = 500; private int childOverlap = 80; }
    @Data public static class Worker { private boolean enabled = true; private long pollDelayMs = 2000; private int maxAttempts = 4; }
    @Data public static class Upload { private long maxBytes = 10 * 1024 * 1024; private int maxPdfPages = 200; }
    @Data public static class Memory { private int recentMessageLimit = 16; private int summaryTriggerCount = 24; private int summarySourceLimit = 40; }
}
