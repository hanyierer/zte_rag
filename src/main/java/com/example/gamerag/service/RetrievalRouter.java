package com.example.gamerag.service;
// 根据Query 理解结果生成检索计划
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.model.QueryAnalysis;
import com.example.gamerag.model.RetrievalPlan;
import com.example.gamerag.model.RetrievalStrategy;
import com.example.gamerag.util.TextUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class RetrievalRouter {
    private final RagProperties properties;

    public RetrievalRouter(RagProperties properties) {
        this.properties = properties;
    }

    public RetrievalPlan buildPlan(QueryAnalysis analysis) {
        RetrievalPlan plan = new RetrievalPlan();
        RetrievalStrategy strategy = analysis.getStrategy();
        boolean denseEnabled = properties.getMilvus().isDenseSearchEnabled();
        boolean sparseEnabled = properties.getMilvus().isSparseSearchEnabled();
        boolean keywordEnabled = properties.getMilvus().isKeywordQueryEnabled();

        if (strategy == RetrievalStrategy.VECTOR_FIRST) {
            plan.setDenseVector(denseEnabled);
            plan.setSparseBm25(sparseEnabled);
            plan.setKeywordQuery(false);
        } else if (strategy == RetrievalStrategy.KEYWORD_FIRST || strategy == RetrievalStrategy.EXACT_MATCH) {
            plan.setDenseVector(denseEnabled);
            plan.setSparseBm25(sparseEnabled);
            plan.setKeywordQuery(keywordEnabled);
        } else {
            plan.setDenseVector(denseEnabled);
            plan.setSparseBm25(sparseEnabled);
            plan.setKeywordQuery(keywordEnabled);
        }

        List<String> queries = new ArrayList<>();
        queries.add(analysis.getRewrittenQuery());
        queries.addAll(analysis.getExpandedQueries());
        if (queries.stream().noneMatch(q -> q != null && !q.isBlank())) {
            queries.add(analysis.getOriginalQuery());
        }
        plan.setSearchQueries(TextUtils.distinctNonBlank(queries, Math.max(1, properties.getQueryUnderstanding().getMaxExpandedQueries() + 1)));
        plan.setFilterExpression(buildFilterExpression(analysis.getFilters()));
        return plan;
    }

    private String buildFilterExpression(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return null;
        StringJoiner joiner = new StringJoiner(" && ");
        filters.forEach((field, value) -> {
            if (field != null && !field.isBlank() && value != null && !value.isBlank()) {
                joiner.add(field + " == \"" + TextUtils.escapeMilvusString(value) + "\"");
            }
        });
        String expr = joiner.toString();
        return expr.isBlank() ? null : expr;
    }
}
