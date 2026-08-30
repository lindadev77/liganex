package tech.liganex.studio.module.rag.splitter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ParentChildSplitter {
    private final RecursiveTextSplitter parentSplitter;
    private final RecursiveTextSplitter childSplitter;

    public ParentChildSplitter(int parentSize, int childSize, int childOverlap) {
        if (parentSize < childSize) {
            throw new IllegalArgumentException("parent size must be greater than or equal to child size");
        }
        this.parentSplitter = new RecursiveTextSplitter(parentSize, 0);
        this.childSplitter = new RecursiveTextSplitter(childSize, childOverlap);
    }

    public List<Chunk> split(long documentId, String text) {
        List<Chunk> chunks = new ArrayList<>();
        int ordinal = 0;
        for (RecursiveTextSplitter.Span parent : parentSplitter.split(text)) {
            String parentId = stableId(documentId, "parent", parent.startOffset(), parent.endOffset());
            for (RecursiveTextSplitter.Span child : childSplitter.split(parent.text())) {
                int start = parent.startOffset() + child.startOffset();
                int end = Math.min(parent.endOffset(), parent.startOffset() + child.endOffset());
                String chunkId = stableId(documentId, "child", start, end);
                chunks.add(new Chunk(chunkId, parentId, ordinal++, child.text(), parent.text(), start, end));
            }
        }
        return chunks;
    }

    private static String stableId(long documentId, String type, int start, int end) {
        return UUID.nameUUIDFromBytes((documentId + ":" + type + ":" + start + ":" + end)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record Chunk(String chunkId, String parentChunkId, int ordinal, String content,
                        String parentContent, int startOffset, int endOffset) {
    }
}
