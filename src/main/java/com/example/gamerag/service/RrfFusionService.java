package com.example.gamerag.service;
// RRF 融合去重
import com.example.gamerag.model.SearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RrfFusionService {
    public List<SearchHit> fuse(List<List<SearchHit>> rankedLists, int rrfK, int topK) {
        Map<String, SearchHit> bestHit = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();

        if (rankedLists == null) return List.of();
        for (List<SearchHit> list : rankedLists) {
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                SearchHit hit = list.get(i);
                if (hit == null || hit.getId() == null) continue;
                int rank = i + 1;
                scores.merge(hit.getId(), 1.0 / (rrfK + rank), Double::sum);
                bestHit.compute(hit.getId(), (id, old) -> chooseBetter(old, hit));
            }
        }

        List<SearchHit> fused = new ArrayList<>();
        for (Map.Entry<String, SearchHit> entry : bestHit.entrySet()) {
            SearchHit copy = entry.getValue().copy();
            copy.setScore(scores.getOrDefault(entry.getKey(), 0.0));
            fused.add(copy);
        }
        fused.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        for (int i = 0; i < fused.size(); i++) {
            fused.get(i).setRank(i + 1);
        }
        return fused.size() > topK ? fused.subList(0, topK) : fused;
    }

    private SearchHit chooseBetter(SearchHit oldHit, SearchHit newHit) {
        if (oldHit == null) return newHit;
        if (newHit.getContent() != null && newHit.getContent().length() > oldHit.getContent().length()) {
            return newHit;
        }
        return oldHit;
    }
}
