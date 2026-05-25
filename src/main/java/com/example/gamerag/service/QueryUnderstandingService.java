package com.example.gamerag.service;
// Query 理解 返回结构化的检索信息
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.model.IntentType;
import com.example.gamerag.model.QueryAnalysis;
import com.example.gamerag.model.RetrievalStrategy;
import com.example.gamerag.util.JsonExtractUtils;
import com.example.gamerag.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QueryUnderstandingService {
    private static final Logger log = LoggerFactory.getLogger(QueryUnderstandingService.class);
    private final DoubaoClient doubaoClient;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public QueryUnderstandingService(DoubaoClient doubaoClient, RagProperties properties, ObjectMapper objectMapper) {
        this.doubaoClient = doubaoClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public QueryAnalysis analyze(String rawQuery, String gameId, Map<String, String> requestFilters) {
        if (!properties.getQueryUnderstanding().isEnabled()) {
            return fallback(rawQuery, gameId, requestFilters);
        }
        try {
            QueryAnalysis analysis = analyzeByLlm(rawQuery, gameId, requestFilters);
            if (analysis.getRewrittenQuery() == null || analysis.getRewrittenQuery().isBlank()) {
                analysis.setRewrittenQuery(rawQuery);
            }
            analysis.setOriginalQuery(rawQuery);
            mergeRequestFilters(analysis, gameId, requestFilters);
            analysis.setExpandedQueries(TextUtils.distinctNonBlank(analysis.getExpandedQueries(), properties.getQueryUnderstanding().getMaxExpandedQueries()));
            return analysis;
        } catch (Exception e) {
            log.warn("Query understanding by LLM failed, fallback to rules: {}", e.getMessage());
            if (properties.getQueryUnderstanding().isFallbackToRules()) {
                return fallback(rawQuery, gameId, requestFilters);
            }
            throw new IllegalStateException("Query understanding by LLM failed and fallback is disabled", e);
        }
    }

    private QueryAnalysis analyzeByLlm(String rawQuery, String gameId, Map<String, String> requestFilters) throws Exception {
        String system = "你是游戏知识库 RAG 系统的 Query 理解器，只输出 JSON，不要解释。";
        String user = """
                请分析玩家问题，并输出严格 JSON：
                {
                  "intent": "QUEST_GUIDE|ITEM_QUERY|CHARACTER_BUILD|MAP_LOCATION|BOSS_STRATEGY|GAME_MECHANIC|PATCH_OR_VERSION|LORE|TROUBLESHOOTING|GENERAL_QA",
                  "strategy": "VECTOR_FIRST|KEYWORD_FIRST|HYBRID|EXACT_MATCH",
                  "rewrittenQuery": "适合向量检索的完整中文查询",
                  "expandedQueries": ["同义表达或补充检索式，最多4条"],
                  "keywords": ["适合关键词/BM25检索的专名、道具、NPC、地图、任务名、版本号"],
                  "entities": {"item":"", "npc":"", "map":"", "quest":"", "boss":"", "version":""},
                  "filters": {"doc_type":"可选，例如 guide/wiki/patch"}
                }

                要求：
                1. 不要编造游戏知识，只做查询理解。
                2. 如果问题含任务名、道具名、NPC、地图、版本号，应放入 keywords/entities。
                3. 如果问题问“在哪/位置/坐标/路线”，intent 倾向 MAP_LOCATION 或 QUEST_GUIDE。
                4. 如果含精确专名、版本号、ID，strategy 倾向 KEYWORD_FIRST 或 EXACT_MATCH；一般问答用 HYBRID。

                gameId: %s
                requestFilters: %s
                rawQuery: %s
                """.formatted(gameId, requestFilters, rawQuery);
        String content = doubaoClient.chat(system, user);
        JsonNode json = objectMapper.readTree(JsonExtractUtils.extractFirstJsonObject(content));
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setIntent(parseEnum(IntentType.class, json.path("intent").asText("GENERAL_QA"), IntentType.GENERAL_QA));
        analysis.setStrategy(parseEnum(RetrievalStrategy.class, json.path("strategy").asText("HYBRID"), RetrievalStrategy.HYBRID));
        analysis.setRewrittenQuery(json.path("rewrittenQuery").asText(rawQuery));
        analysis.setExpandedQueries(readStringList(json.path("expandedQueries")));
        analysis.setKeywords(readStringList(json.path("keywords")));
        analysis.setEntities(readStringMap(json.path("entities")));
        analysis.setFilters(readStringMap(json.path("filters")));
        return analysis;
    }

    private QueryAnalysis fallback(String rawQuery, String gameId, Map<String, String> requestFilters) {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setOriginalQuery(rawQuery);
        analysis.setRewrittenQuery(rawQuery);
        analysis.setKeywords(extractSimpleKeywords(rawQuery));
        analysis.setExpandedQueries(expandByRules(rawQuery));
        analysis.setIntent(ruleIntent(rawQuery));
        analysis.setStrategy(ruleStrategy(rawQuery));
        mergeRequestFilters(analysis, gameId, requestFilters);
        return analysis;
    }

    private void mergeRequestFilters(QueryAnalysis analysis, String gameId, Map<String, String> requestFilters) {
        Map<String, String> filters = new LinkedHashMap<>();
        if (analysis.getFilters() != null) {
            filters.putAll(analysis.getFilters());
        }
        if (requestFilters != null) {
            filters.putAll(requestFilters);
        }
        if (gameId != null && !gameId.isBlank()) {
            filters.put(properties.getMilvus().getGameField(), gameId);
        }
        analysis.setFilters(filters);
    }

    private IntentType ruleIntent(String q) {
        String s = q == null ? "" : q.toLowerCase(Locale.ROOT);
        if (s.contains("boss") || s.contains("打法") || s.contains("怎么打")) return IntentType.BOSS_STRATEGY;
        if (s.contains("在哪") || s.contains("位置") || s.contains("坐标") || s.contains("地图")) return IntentType.MAP_LOCATION;
        if (s.contains("任务") || s.contains("流程") || s.contains("怎么过")) return IntentType.QUEST_GUIDE;
        if (s.contains("装备") || s.contains("配装") || s.contains("build") || s.contains("天赋")) return IntentType.CHARACTER_BUILD;
        if (s.contains("道具") || s.contains("材料") || s.contains("掉落")) return IntentType.ITEM_QUERY;
        if (s.contains("版本") || s.contains("更新") || s.contains("补丁")) return IntentType.PATCH_OR_VERSION;
        if (s.contains("机制") || s.contains("规则") || s.contains("公式")) return IntentType.GAME_MECHANIC;
        if (s.contains("剧情") || s.contains("背景") || s.contains(" lore")) return IntentType.LORE;
        return IntentType.GENERAL_QA;
    }

    private RetrievalStrategy ruleStrategy(String q) {
        String s = q == null ? "" : q;
        if (s.matches(".*(ID|id|编号|版本|v\\d+|\\d+\\.\\d+).*")) return RetrievalStrategy.KEYWORD_FIRST;
        if (s.length() <= 12) return RetrievalStrategy.KEYWORD_FIRST;
        return RetrievalStrategy.HYBRID;
    }

    private List<String> expandByRules(String rawQuery) {
        List<String> queries = new ArrayList<>();
        if (rawQuery != null && !rawQuery.isBlank()) {
            queries.add(rawQuery);
            if (rawQuery.contains("在哪")) queries.add(rawQuery.replace("在哪", "位置 获取路线 地点"));
            if (rawQuery.contains("怎么打")) queries.add(rawQuery.replace("怎么打", "打法 机制 阶段 技巧"));
            if (rawQuery.contains("怎么获得")) queries.add(rawQuery.replace("怎么获得", "获取方式 掉落 来源 条件"));
        }
        return TextUtils.distinctNonBlank(queries, properties.getQueryUnderstanding().getMaxExpandedQueries());
    }

    private List<String> extractSimpleKeywords(String rawQuery) {
        if (rawQuery == null) return List.of();
        String cleaned = rawQuery.replaceAll("[，。！？；：,.!?;:\\[\\]（）()《》<>]", " ");
        String[] parts = cleaned.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2 && part.length() <= 30) {
                out.add(part);
            }
        }
        if (out.isEmpty() && !rawQuery.isBlank()) out.add(rawQuery.trim());
        return TextUtils.distinctNonBlank(out, 8);
    }

    private List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("");
                if (!value.isBlank()) list.add(value);
            }
        }
        return list;
    }

    private Map<String, String> readStringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String value = entry.getValue().asText("");
                if (!value.isBlank()) map.put(entry.getKey(), value);
            });
        }
        return map;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
