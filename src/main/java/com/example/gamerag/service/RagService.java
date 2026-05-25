package com.example.gamerag.service;
// 完整的RAG流程 总调度类
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.dto.RagQueryRequest;
import com.example.gamerag.dto.RagQueryResponse;
import com.example.gamerag.dto.ReferenceDto;
import com.example.gamerag.model.QueryAnalysis;
import com.example.gamerag.model.RetrievalPlan;
import com.example.gamerag.model.SearchHit;
import com.example.gamerag.util.TextUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagService {
    private final RagProperties properties;
    private final RagCacheService cacheService;
    private final QueryUnderstandingService queryUnderstandingService;
    private final RetrievalRouter retrievalRouter;
    private final MilvusRetrievalService milvusRetrievalService;
    private final RrfFusionService rrfFusionService;
    private final RerankService rerankService;
    private final PromptBuilder promptBuilder;
    private final DoubaoClient doubaoClient;
    // 注入所有子模块
    public RagService(RagProperties properties,
                      RagCacheService cacheService,
                      QueryUnderstandingService queryUnderstandingService,
                      RetrievalRouter retrievalRouter,
                      MilvusRetrievalService milvusRetrievalService,
                      RrfFusionService rrfFusionService,
                      RerankService rerankService,
                      PromptBuilder promptBuilder,
                      DoubaoClient doubaoClient) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.queryUnderstandingService = queryUnderstandingService;
        this.retrievalRouter = retrievalRouter;
        this.milvusRetrievalService = milvusRetrievalService;
        this.rrfFusionService = rrfFusionService;
        this.rerankService = rerankService;
        this.promptBuilder = promptBuilder;
        this.doubaoClient = doubaoClient;
    }
    // RAG 主入口
    public RagQueryResponse query(RagQueryRequest request) {
        return cacheService.get(request).orElseGet(() -> execute(request));
    }
    // RAG 流水线
    private RagQueryResponse execute(RagQueryRequest request) {
        long start = System.currentTimeMillis();
        QueryAnalysis analysis = queryUnderstandingService.analyze(request.getQuery(), request.getGameId(), request.getFilters());
        RetrievalPlan plan = retrievalRouter.buildPlan(analysis);

        List<List<SearchHit>> recallLists = milvusRetrievalService.recall(plan, analysis.getKeywords());
        int rawHitCount = recallLists.stream().mapToInt(List::size).sum();
        List<SearchHit> fused = rrfFusionService.fuse(recallLists, properties.getRetrieval().getRrfK(), properties.getRetrieval().getFusedTopK());

        int finalTopK = request.getTopK() != null && request.getTopK() > 0
                ? Math.min(request.getTopK(), 20)
                : properties.getRetrieval().getFinalTopK();
        List<SearchHit> topDocs = rerankService.rerank(analysis, fused, finalTopK).stream()
                .filter(h -> h.getScore() >= properties.getRetrieval().getMinScore())
                .toList();

        String answer;
        if (topDocs.isEmpty()) {
            answer = "知识库中没有足够信息回答这个问题。你可以尝试补充游戏名、版本、任务名、道具名或地图名。";
        } else {
            String userPrompt = promptBuilder.buildUserPrompt(request.getQuery(), analysis, topDocs);
            answer = doubaoClient.chat(properties.getPrompt().getSystemMessage(), userPrompt);
        }

        RagQueryResponse response = new RagQueryResponse();
        response.setQuery(request.getQuery());
        response.setAnswer(answer);
        response.setCacheHit(false);
        response.setQueryAnalysis(analysis);
        response.setReferences(toReferences(topDocs));
        if (Boolean.TRUE.equals(request.getDebug())) {
            Map<String, Object> debug = new LinkedHashMap<>();
            Map<String, Object> planDebug = new LinkedHashMap<>();
            planDebug.put("denseVector", plan.isDenseVector());
            planDebug.put("sparseBm25", plan.isSparseBm25());
            planDebug.put("keywordQuery", plan.isKeywordQuery());
            planDebug.put("searchQueries", plan.getSearchQueries());
            planDebug.put("filterExpression", plan.getFilterExpression());
            debug.put("retrievalPlan", planDebug);
            debug.put("embeddingModel", properties.getEmbedding().getModel());
            debug.put("rerankerModel", properties.getReranker().isEnabled() ? properties.getReranker().getModel() : "disabled");
            debug.put("rawHitCount", rawHitCount);
            debug.put("fusedHitCount", fused.size());
            debug.put("latencyMs", System.currentTimeMillis() - start);
            debug.put("cacheSize", cacheService.estimatedSize());
            response.setDebug(debug);
        }
        cacheService.put(request, response);
        return response;
    }
    // 转前端返回用的ReferenceDto
    private List<ReferenceDto> toReferences(List<SearchHit> docs) {
        List<ReferenceDto> refs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            SearchHit doc = docs.get(i);
            ReferenceDto ref = new ReferenceDto();
            ref.setIndex(i + 1);
            ref.setId(doc.getId());
            ref.setTitle(doc.getTitle());
            ref.setSource(doc.getSource());
            ref.setUrl(doc.getUrl());
            ref.setScore(doc.getScore());
            ref.setSnippet(TextUtils.truncate(doc.getContent(), 240));
            refs.add(ref);
        }
        return refs;
    }
}
