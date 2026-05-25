package com.example.gamerag.model;
// 检索计划
import java.util.ArrayList;
import java.util.List;

public class RetrievalPlan {
    private boolean denseVector;
    private boolean sparseBm25;
    private boolean keywordQuery;
    private List<String> searchQueries = new ArrayList<>();
    private String filterExpression;

    public boolean isDenseVector() { return denseVector; }
    public void setDenseVector(boolean denseVector) { this.denseVector = denseVector; }
    public boolean isSparseBm25() { return sparseBm25; }
    public void setSparseBm25(boolean sparseBm25) { this.sparseBm25 = sparseBm25; }
    public boolean isKeywordQuery() { return keywordQuery; }
    public void setKeywordQuery(boolean keywordQuery) { this.keywordQuery = keywordQuery; }
    public List<String> getSearchQueries() { return searchQueries; }
    public void setSearchQueries(List<String> searchQueries) { this.searchQueries = searchQueries == null ? new ArrayList<>() : searchQueries; }
    public String getFilterExpression() { return filterExpression; }
    public void setFilterExpression(String filterExpression) { this.filterExpression = filterExpression; }
}
