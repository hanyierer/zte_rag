package com.example.gamerag.service;
// 负责构造最终给豆包生成答案的 Prompt。
import com.example.gamerag.config.RagProperties;
import com.example.gamerag.model.QueryAnalysis;
import com.example.gamerag.model.SearchHit;
import com.example.gamerag.util.TextUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptBuilder {
    private final RagProperties properties;

    public PromptBuilder(RagProperties properties) {
        this.properties = properties;
    }

    public String buildUserPrompt(String rawQuery, QueryAnalysis analysis, List<SearchHit> docs) {
        StringBuilder ctx = new StringBuilder();
        int maxContext = properties.getPrompt().getMaxContextChars();
        int maxDoc = properties.getPrompt().getMaxDocChars();
        for (int i = 0; i < docs.size(); i++) {
            SearchHit doc = docs.get(i);
            String block = """
                    [%d]
                    标题：%s
                    来源：%s
                    URL：%s
                    内容：%s

                    """.formatted(
                    i + 1,
                    safe(doc.getTitle()),
                    safe(doc.getSource()),
                    safe(doc.getUrl()),
                    TextUtils.truncate(safe(doc.getContent()), maxDoc)
            );
            if (ctx.length() + block.length() > maxContext) break;
            ctx.append(block);
        }

        return """
                玩家原始问题：%s
                Query理解：
                - intent: %s
                - rewrittenQuery: %s
                - keywords: %s
                - entities: %s

                知识库片段：
                %s

                请基于以上片段回答玩家问题。
                输出要求：
                1. 先直接回答结论，再给步骤/条件/注意事项。
                2. 每个关键结论后用 [1]、[2] 标注依据。
                3. 如果片段之间有冲突，请说明可能与版本或来源有关。
                4. 如果证据不足，不要猜测，说明知识库中没有足够信息。
                """.formatted(rawQuery, analysis.getIntent(), analysis.getRewrittenQuery(), analysis.getKeywords(), analysis.getEntities(), ctx);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
