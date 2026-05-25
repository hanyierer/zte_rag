package com.example.gamerag.dto;
// 表示用户请求，读取或设置请求参数
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

public class RagQueryRequest {
    @NotBlank(message = "query 不能为空")
    @Size(max = 2000, message = "query 过长")
    private String query;
    private String gameId;
    private Map<String, String> filters = new LinkedHashMap<>();
    private Integer topK;
    private Boolean bypassCache = false;
    private Boolean debug = false;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public Map<String, String> getFilters() { return filters; }
    public void setFilters(Map<String, String> filters) { this.filters = filters == null ? new LinkedHashMap<>() : filters; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Boolean getBypassCache() { return bypassCache; }
    public void setBypassCache(Boolean bypassCache) { this.bypassCache = bypassCache; }
    public Boolean getDebug() { return debug; }
    public void setDebug(Boolean debug) { this.debug = debug; }
}
