package com.example.gamerag.dto;
// RAG 回答结果
import com.example.gamerag.model.QueryAnalysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagQueryResponse {
    private String query; // 用户问题
    private String answer;
    private boolean cacheHit;
    private List<ReferenceDto> references = new ArrayList<>();
    private QueryAnalysis queryAnalysis;
    private Map<String, Object> debug = new LinkedHashMap<>();
    private Instant createdAt = Instant.now();

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public boolean isCacheHit() { return cacheHit; }
    public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }
    public List<ReferenceDto> getReferences() { return references; }
    public void setReferences(List<ReferenceDto> references) { this.references = references; }
    public QueryAnalysis getQueryAnalysis() { return queryAnalysis; }
    public void setQueryAnalysis(QueryAnalysis queryAnalysis) { this.queryAnalysis = queryAnalysis; }
    public Map<String, Object> getDebug() { return debug; }
    public void setDebug(Map<String, Object> debug) { this.debug = debug; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
