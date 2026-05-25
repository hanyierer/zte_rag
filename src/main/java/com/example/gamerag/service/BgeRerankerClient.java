package com.example.gamerag.service;
// 负责调用bge-reranker 模型API
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.exception.RagException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BgeRerankerClient {
    private static final Logger log = LoggerFactory.getLogger(BgeRerankerClient.class);

    private final RagProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public BgeRerankerClient(RagProperties properties, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder.clone()
                .baseUrl(properties.getReranker().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<RerankScore> rerank(String query, List<String> documents, int topN) {
        ensureConfigured();
        if (documents == null || documents.isEmpty()) return List.of();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getReranker().getModel());
        body.put("query", query == null ? "" : query);
        body.put("documents", documents);
        body.put("top_n", Math.min(Math.max(1, topN), documents.size()));
        body.put("return_documents", properties.getReranker().isReturnDocuments());

        JsonNode root = postJson(properties.getReranker().getEndpointPath(), body);
        return parseScores(root);
    }

    private JsonNode postJson(String path, Map<String, Object> body) {
        try {
            WebClient.RequestHeadersSpec<?> request = webClient.post()
                    .uri(path)
                    .bodyValue(body);
            if (properties.getReranker().getApiKey() != null && !properties.getReranker().getApiKey().isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getReranker().getApiKey());
            }
            String response = request
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(err -> Mono.error(new RagException("BGE Reranker API 调用失败: HTTP " + resp.statusCode() + " " + err))))
                    .bodyToMono(String.class)
                    .block(properties.getReranker().getTimeout());
            return objectMapper.readTree(response);
        } catch (RagException e) {
            throw e;
        } catch (Exception e) {
            log.error("BGE reranker API error", e);
            throw new RagException("BGE Reranker API 调用异常: " + e.getMessage(), e);
        }
    }

    private List<RerankScore> parseScores(JsonNode root) {
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            results = root.path("data");
        }
        if (!results.isArray() && root.isArray()) {
            results = root;
        }
        if (!results.isArray()) {
            throw new RagException("BGE Reranker API 返回格式不支持，未找到 results/data 数组: " + root);
        }

        List<RerankScore> scores = new ArrayList<>();
        for (JsonNode item : results) {
            int index = firstInt(item, -1, "index", "document_index", "doc_index");
            if (index < 0) {
                continue;
            }
            double score = firstDouble(item, Double.NaN, "relevance_score", "score", "similarity", "value");
            if (Double.isNaN(score)) {
                continue;
            }
            scores.add(new RerankScore(index, score));
        }
        if (scores.isEmpty()) {
            throw new RagException("BGE Reranker API 未返回有效 index/score: " + root);
        }
        return scores;
    }

    private int firstInt(JsonNode node, int fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isInt() || value.isLong() || value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    private double firstDouble(JsonNode node, double fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    private void ensureConfigured() {
        if (!properties.getReranker().isEnabled()) {
            throw new RagException("BGE Reranker 已关闭");
        }
        if (properties.getReranker().getModel() == null || properties.getReranker().getModel().isBlank()) {
            throw new RagException("未配置 BGE_RERANK_MODEL / rag.reranker.model");
        }
        if (properties.getReranker().getBaseUrl() == null || properties.getReranker().getBaseUrl().isBlank()) {
            throw new RagException("未配置 BGE_RERANK_BASE_URL / rag.reranker.base-url");
        }
        if (properties.getReranker().isRequireApiKey()
                && (properties.getReranker().getApiKey() == null || properties.getReranker().getApiKey().isBlank())) {
            throw new RagException("未配置 BGE_RERANK_API_KEY / rag.reranker.api-key；如果是本地无鉴权服务，可将 rag.reranker.require-api-key=false");
        }
    }

    public record RerankScore(int index, double score) {
    }
}
