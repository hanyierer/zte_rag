package com.example.gamerag.service;
// 测试 RRF 融合逻辑。
import com.example.gamerag.model.RecallChannel;
import com.example.gamerag.model.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionServiceTest {
    @Test
    void fuseShouldDeduplicateAndRankByRrf() {
        RrfFusionService service = new RrfFusionService();
        SearchHit a1 = hit("A", 1, RecallChannel.DENSE_VECTOR);
        SearchHit b1 = hit("B", 2, RecallChannel.DENSE_VECTOR);
        SearchHit a2 = hit("A", 1, RecallChannel.SPARSE_BM25);
        SearchHit c2 = hit("C", 2, RecallChannel.SPARSE_BM25);

        List<SearchHit> fused = service.fuse(List.of(List.of(a1, b1), List.of(a2, c2)), 60, 10);

        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).getId()).isEqualTo("A");
        assertThat(fused.get(0).getScore()).isGreaterThan(fused.get(1).getScore());
    }

    private SearchHit hit(String id, int rank, RecallChannel channel) {
        SearchHit h = new SearchHit(id, "title" + id, "content" + id, "src", "", 1.0 / rank, channel);
        h.setRank(rank);
        return h;
    }
}
