package com.example.gamerag.service;
// RAG 缓存
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.dto.RagQueryRequest;
import com.example.gamerag.dto.RagQueryResponse;
import com.example.gamerag.util.TextUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RagCacheService {
    private final RagProperties properties;
    private final Cache<String, RagQueryResponse> cache;

    public RagCacheService(RagProperties properties) {
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(properties.getCache().getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public Optional<RagQueryResponse> get(RagQueryRequest request) {
        if (!properties.getCache().isEnabled() || Boolean.TRUE.equals(request.getBypassCache())) {
            return Optional.empty();
        }
        RagQueryResponse response = cache.getIfPresent(key(request));
        if (response == null) return Optional.empty();
        response.setCacheHit(true);
        return Optional.of(response);
    }

    public void put(RagQueryRequest request, RagQueryResponse response) {
        if (!properties.getCache().isEnabled() || Boolean.TRUE.equals(request.getBypassCache())) return;
        cache.put(key(request), response);
    }

    public void clear() {
        cache.invalidateAll();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    private String key(RagQueryRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("query", TextUtils.normalizeQuery(request.getQuery()));
        map.put("gameId", request.getGameId());
        map.put("filters", request.getFilters());
        map.put("topK", request.getTopK());
        return TextUtils.sha256(map.toString());
    }
}
