package tech.liganex.studio.module.rag.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridTermTokenizerTest {
    private final HybridTermTokenizer tokenizer = new HybridTermTokenizer();

    @Test
    void createsHanUnigramsBigramsAndNormalizedLatinTerms() {
        assertThat(tokenizer.terms("库存 ABC-123 库存"))
                .isEqualTo("库 库存 存 abc-123");
    }

    @Test
    void indexingAndQueryingAreDeterministic() {
        assertThat(tokenizer.terms("Ｏｒｄｅｒ 订单"))
                .isEqualTo(tokenizer.terms("order 订单"));
    }
}
