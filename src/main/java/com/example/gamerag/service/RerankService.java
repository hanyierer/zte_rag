package com.example.gamerag.service;
// 精排
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.model.QueryAnalysis;
import com.example.gamerag.model.SearchHit;
import com.example.gamerag.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RerankService {
    private static final Logger log = LoggerFactory.getLogger(RerankService.class);
    private final RagProperties properties;
    private final BgeRerankerClient bgeRerankerClient;

    public RerankService(RagProperties properties, BgeRerankerClient bgeRerankerClient) {
        this.properties = properties;
        this.bgeRerankerClient = bgeRerankerClient;
    }

    public List<SearchHit> rerank(QueryAnalysis analysis, List<SearchHit> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<SearchHit> limited = candidates.size() > properties.getRetrieval().getRerankCandidateK()
                ? new ArrayList<>(candidates.subList(0, properties.getRetrieval().getRerankCandidateK()))
                : new ArrayList<>(candidates);

        List<SearchHit> reranked;
        if (properties.getReranker().isEnabled()) {
            try {
                reranked = rerankByBge(analysis, limited);
            } catch (Exception e) {
                if (!properties.getReranker().isFallbackToHeuristic()) {
                    throw e;
                }
                log.warn("BGE rerank failed, fallback to heuristic: {}", e.getMessage());
                reranked = rerankByHeuristic(analysis, limited);
            }
        } else {
            reranked = rerankByHeuristic(analysis, limited);
        }
        return reranked.size() > topK ? reranked.subList(0, topK) : reranked;
    }

    private List<SearchHit> rerankByBge(QueryAnalysis analysis, List<SearchHit> candidates) {
        String query = chooseRerankQuery(analysis);
        List<String> documents = candidates.stream()
                .map(this::formatDocumentForRerank)
                .toList();

        List<BgeRerankerClient.RerankScore> scores = bgeRerankerClient.rerank(query, documents, candidates.size());
        Map<Integer, Double> scoreByIndex = new HashMap<>();
        for (BgeRerankerClient.RerankScore score : scores) {
            scoreByIndex.put(score.index(), score.score());
        }

        double minRecall = candidates.stream().mapToDouble(SearchHit::getScore).min().orElse(0.0);
        double maxRecall = candidates.stream().mapToDouble(SearchHit::getScore).max().orElse(1.0);
        double rerankWeight = clamp(properties.getReranker().getRerankWeight(), 0.0, 1.0);

        List<SearchHit> reranked = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            SearchHit hit = candidates.get(i);
            Double rawScore = scoreByIndex.get(i);
            if (rawScore == null) {
                continue;
            }
            double normalizedRerankScore = normalizeRerankScore(rawScore);
            double normalizedRecallScore = normalizeRecallScore(hit.getScore(), minRecall, maxRecall);
            double finalScore = rerankWeight * normalizedRerankScore + (1.0 - rerankWeight) * normalizedRecallScore;

            hit.getMetadata().put("rerank_model", properties.getReranker().getModel());
            hit.getMetadata().put("bge_rerank_raw_score", rawScore);
            hit.getMetadata().put("bge_rerank_normalized_score", normalizedRerankScore);
            hit.getMetadata().put("rrf_score_before_rerank", hit.getScore());
            hit.setScore(finalScore);
            reranked.add(hit);
        }

        // 如果 API top_n 小于候选数，未返回的候选会被放到后面，避免结果数不足。
        if (reranked.size() < candidates.size()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (!scoreByIndex.containsKey(i)) {
                    SearchHit hit = candidates.get(i);
                    hit.getMetadata().put("rerank_model", properties.getReranker().getModel());
                    hit.getMetadata().put("bge_rerank_missing", true);
                    hit.setScore((1.0 - rerankWeight) * normalizeRecallScore(hit.getScore(), minRecall, maxRecall));
                    reranked.add(hit);
                }
            }
        }

        reranked.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        for (int i = 0; i < reranked.size(); i++) {
            reranked.get(i).setRank(i + 1);
        }
        return reranked;
    }

    private List<SearchHit> rerankByHeuristic(QueryAnalysis analysis, List<SearchHit> candidates) {
        List<String> keywords = analysis.getKeywords();
        for (SearchHit hit : candidates) {
            int keywordCount = TextUtils.keywordHitCount(hit.getTitle() + "\n" + hit.getContent(), keywords);
            double score = hit.getScore() + keywordCount * 0.03;
            hit.getMetadata().put("rerank_model", "heuristic_fallback");
            hit.setScore(score);
        }
        candidates.sort(Comparator.comparingDouble(SearchHit::getScore).reversed());
        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setRank(i + 1);
        }
        return candidates;
    }

    private String chooseRerankQuery(QueryAnalysis analysis) {
        if (analysis == null) return "";
        if (analysis.getRewrittenQuery() != null && !analysis.getRewrittenQuery().isBlank()) {
            return analysis.getRewrittenQuery();
        }
        return analysis.getOriginalQuery() == null ? "" : analysis.getOriginalQuery();
    }

    private String formatDocumentForRerank(SearchHit hit) {
        String title = hit.getTitle() == null ? "" : hit.getTitle();
        String content = hit.getContent() == null ? "" : hit.getContent();
        String document = title.isBlank() ? content : "标题：" + title + "\n内容：" + content;
        return TextUtils.truncate(document, Math.max(200, properties.getReranker().getMaxDocumentChars()));
    }

    /**
     * 多数 OpenAI/Cohere/SiliconFlow 兼容 Rerank API 返回 0~1 relevance_score；
     * 若自建服务返回 cross-encoder 原始 logit，则用 sigmoid 压到 0~1。
     */
    private double normalizeRerankScore(double rawScore) {
        if (rawScore >= 0.0 && rawScore <= 1.0) {
            return rawScore;
        }
        return 1.0 / (1.0 + Math.exp(-rawScore));
    }

    private double normalizeRecallScore(double score, double min, double max) {
        if (Math.abs(max - min) < 1e-12) {
            return 0.5;
        }
        return (score - min) / (max - min);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
