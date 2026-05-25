package com.example.gamerag.model;
// Query 理解结果
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryAnalysis {
    private IntentType intent = IntentType.GENERAL_QA;
    private RetrievalStrategy strategy = RetrievalStrategy.HYBRID;
    private String originalQuery;
    private String rewrittenQuery;
    private List<String> expandedQueries = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    private Map<String, String> entities = new LinkedHashMap<>();
    private Map<String, String> filters = new LinkedHashMap<>();

    public IntentType getIntent() { return intent; }
    public void setIntent(IntentType intent) { this.intent = intent == null ? IntentType.GENERAL_QA : intent; }
    public RetrievalStrategy getStrategy() { return strategy; }
    public void setStrategy(RetrievalStrategy strategy) { this.strategy = strategy == null ? RetrievalStrategy.HYBRID : strategy; }
    public String getOriginalQuery() { return originalQuery; }
    public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }
    public List<String> getExpandedQueries() { return expandedQueries; }
    public void setExpandedQueries(List<String> expandedQueries) { this.expandedQueries = expandedQueries == null ? new ArrayList<>() : expandedQueries; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords == null ? new ArrayList<>() : keywords; }
    public Map<String, String> getEntities() { return entities; }
    public void setEntities(Map<String, String> entities) { this.entities = entities == null ? new LinkedHashMap<>() : entities; }
    public Map<String, String> getFilters() { return filters; }
    public void setFilters(Map<String, String> filters) { this.filters = filters == null ? new LinkedHashMap<>() : filters; }
}
