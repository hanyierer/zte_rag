package com.example.gamerag.controller;
// 用户请求接口入口
import com.example.gamerag.dto.CacheClearResponse;
import com.example.gamerag.dto.RagQueryRequest;
import com.example.gamerag.dto.RagQueryResponse;
import com.example.gamerag.service.RagCacheService;
import com.example.gamerag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService ragService;
    private final RagCacheService cacheService;

    public RagController(RagService ragService, RagCacheService cacheService) {
        this.ragService = ragService;
        this.cacheService = cacheService;
    }

    @PostMapping("/query")
    public RagQueryResponse query(@Valid @RequestBody RagQueryRequest request) {
        return ragService.query(request);
    }

    @PostMapping("/cache/clear")
    public CacheClearResponse clearCache() {
        cacheService.clear();
        return new CacheClearResponse(true, "RAG cache cleared");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "cacheSize", cacheService.estimatedSize());
    }
}
