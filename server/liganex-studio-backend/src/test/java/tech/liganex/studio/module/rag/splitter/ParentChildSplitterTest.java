package tech.liganex.studio.module.rag.splitter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParentChildSplitterTest {
    @Test
    void splitsChineseAndEnglishWithStableIdsAndBoundedChildren() {
        ParentChildSplitter splitter = new ParentChildSplitter(40, 18, 4);
        String text = "订单创建成功。Order ABC-123 is ready.\n库存已经同步，请继续处理。";

        var first = splitter.split(7L, text);
        var second = splitter.split(7L, text);

        assertThat(first).isNotEmpty();
        assertThat(first).extracting(ParentChildSplitter.Chunk::chunkId)
                .containsExactlyElementsOf(second.stream().map(ParentChildSplitter.Chunk::chunkId).toList());
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(18);
            assertThat(chunk.parentContent().length()).isLessThanOrEqualTo(40);
        });
    }

    @Test
    void validatesChunkSizes() {
        assertThatThrownBy(() -> new ParentChildSplitter(10, 20, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParentChildSplitter(20, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
