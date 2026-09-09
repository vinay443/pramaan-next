package com.pramaan.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockEmbeddingModelTest {

    private final MockEmbeddingModel model = new MockEmbeddingModel(256);

    @Test
    void deterministicAndUnitLength() {
        float[] a = model.embed("OS-SSH-ROOT-LOGIN disabled on net-banking");
        float[] b = model.embed("OS-SSH-ROOT-LOGIN disabled on net-banking");
        assertThat(a).containsExactly(b);
        assertThat(EmbeddingModel.cosine(a, b)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void sharedVocabularyScoresHigherThanUnrelated() {
        float[] q = model.embed("control OS-SSH-ROOT-LOGIN framework C-SITE ssh root login disabled");
        float[] near = model.embed("control OS-SSH-ROOT-LOGIN framework C-SITE ssh root login not permitted");
        float[] far = model.embed("control TLS-CERT-EXPIRY framework DPSC certificate renewal calendar");
        assertThat(EmbeddingModel.cosine(q, near)).isGreaterThan(EmbeddingModel.cosine(q, far));
    }
}
