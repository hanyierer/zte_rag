package com.example.gamerag.service;
// 负责调用豆包大模型
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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DoubaoClient {
    private static final Logger log = LoggerFactory.getLogger(DoubaoClient.class);

    private final RagProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DoubaoClient(RagProperties properties, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder.clone()
                .baseUrl(properties.getDoubao().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 豆包只负责 Query 理解和最终答案生成。
     * Embedding 已切换为 BGE-M3，Rerank 已切换为 bge-reranker-v2-m3，避免检索向量空间混用。
     */
    public String chat(String systemPrompt, String userPrompt) {
        ensureApiKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getDoubao().getChatModel());
        body.put("temperature", properties.getDoubao().getTemperature());
        body.put("max_tokens", properties.getDoubao().getMaxTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));

        JsonNode root = postJson("/chat/completions", body);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content != null) {
                return content;
            }
        }
        throw new RagException("豆包 Chat API 返回为空: " + root);
    }

    private JsonNode postJson(String path, Map<String, Object> body) {
        Duration timeout = properties.getDoubao().getTimeout();
        try {
            String response = webClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getDoubao().getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(err -> Mono.error(new RagException("豆包 API 调用失败: HTTP " + resp.statusCode() + " " + err))))
                    .bodyToMono(String.class)
                    .block(timeout);
            return objectMapper.readTree(response);
        } catch (RagException e) {
            throw e;
        } catch (Exception e) {
            log.error("Doubao API error", e);
            throw new RagException("豆包 API 调用异常: " + e.getMessage(), e);
        }
    }

    private void ensureApiKey() {
        if (properties.getDoubao().getApiKey() == null || properties.getDoubao().getApiKey().isBlank()) {
            throw new RagException("未配置 DOUBAO_API_KEY / rag.doubao.api-key");
        }
    }
}
