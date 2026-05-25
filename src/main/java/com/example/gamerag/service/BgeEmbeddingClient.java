package com.example.gamerag.service;
// 负责调用BGE-M3嵌入模型API
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
public class BgeEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(BgeEmbeddingClient.class);

    private final RagProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public BgeEmbeddingClient(RagProperties properties, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder.clone()
                .baseUrl(properties.getEmbedding().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<Float> embed(String text) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbedding().getModel());
        body.put("input", text == null ? "" : text);
        if (properties.getEmbedding().getEncodingFormat() != null && !properties.getEmbedding().getEncodingFormat().isBlank()) {
            body.put("encoding_format", properties.getEmbedding().getEncodingFormat());
        }
        if (properties.getEmbedding().getDimensions() > 0) {
            body.put("dimensions", properties.getEmbedding().getDimensions());
        }

        JsonNode root = postJson(properties.getEmbedding().getEndpointPath(), body);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new RagException("BGE-M3 Embedding API 返回为空: " + root);
        }
        JsonNode embedding = data.get(0).path("embedding");
        if (!embedding.isArray()) {
            throw new RagException("BGE-M3 Embedding API 未返回 embedding 数组: " + root);
        }

        List<Float> vector = new ArrayList<>(embedding.size());
        for (JsonNode node : embedding) {
            vector.add((float) node.asDouble());
        }
        if (properties.getEmbedding().isNormalizeQueryVector()) {
            vector = l2Normalize(vector);
        }
        return vector;
    }

    private JsonNode postJson(String path, Map<String, Object> body) {
        try {
            WebClient.RequestHeadersSpec<?> request = webClient.post()
                    .uri(path)
                    .bodyValue(body);
            if (properties.getEmbedding().getApiKey() != null && !properties.getEmbedding().getApiKey().isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getEmbedding().getApiKey());
            }
            String response = request
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(err -> Mono.error(new RagException("BGE-M3 Embedding API 调用失败: HTTP " + resp.statusCode() + " " + err))))
                    .bodyToMono(String.class)
                    .block(properties.getEmbedding().getTimeout());
            return objectMapper.readTree(response);
        } catch (RagException e) {
            throw e;
        } catch (Exception e) {
            log.error("BGE-M3 embedding API error", e);
            throw new RagException("BGE-M3 Embedding API 调用异常: " + e.getMessage(), e);
        }
    }

    private void ensureConfigured() {
        if (properties.getEmbedding().getModel() == null || properties.getEmbedding().getModel().isBlank()) {
            throw new RagException("未配置 BGE_EMBEDDING_MODEL / rag.embedding.model");
        }
        if (properties.getEmbedding().getBaseUrl() == null || properties.getEmbedding().getBaseUrl().isBlank()) {
            throw new RagException("未配置 BGE_BASE_URL / rag.embedding.base-url");
        }
        if (properties.getEmbedding().isRequireApiKey()
                && (properties.getEmbedding().getApiKey() == null || properties.getEmbedding().getApiKey().isBlank())) {
            throw new RagException("未配置 BGE_API_KEY / rag.embedding.api-key；如果是本地无鉴权服务，可将 rag.embedding.require-api-key=false");
        }
    }

    private List<Float> l2Normalize(List<Float> vector) {
        double norm = 0.0;
        for (Float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm <= 1e-12) {
            return vector;
        }
        List<Float> out = new ArrayList<>(vector.size());
        for (Float value : vector) {
            out.add((float) (value / norm));
        }
        return out;
    }
}
