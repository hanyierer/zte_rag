# Game RAG Online：游戏知识库在线检索系统（Spring Boot + Milvus + 豆包 + BGE-M3 + bge-reranker）

本项目只实现 **在线检索与生成**，假设你的游戏知识已经完成离线切分、向量化和 Milvus 入库。

当前版本已经将模型职责拆开：

- **豆包大模型**：Query 理解、最终回答生成。
- **BGE-M3**：线上 Query embedding，与 Milvus dense vector 字段做向量检索。
- **bge-reranker-v2-m3**：对 RRF 融合后的候选知识片段做精排。
- **Milvus**：已经构建好的向量库，负责 dense/sparse/关键词召回。

> 重要：线上使用的 BGE-M3 embedding 模型必须和你离线入库时使用的 embedding 模型一致。若离线 Milvus 索引用的不是 BGE-M3，直接切换线上 embedding 会导致向量空间不一致，召回效果会很差，甚至会因维度不同报错。

---

## 1. 总体流程

```text
用户问题
  ↓
RAG 缓存命中判断
  ↓ miss
Query 理解（豆包）
  ├─ 意图识别
  ├─ Query 改写/扩展
  ├─ 关键词/实体提取
  └─ 检索策略路由
  ↓
多路召回（Milvus）
  ├─ Dense Vector：BGE-M3 query embedding -> Milvus vector search
  ├─ Sparse/BM25：Milvus sparse field / BM25 function，可按需关闭
  └─ Keyword Query：TEXT_MATCH 关键词过滤，可按需关闭
  ↓
RRF 融合去重
  ↓
bge-reranker-v2-m3 精排
  ↓
TopK 片段精选 + Prompt 上下文组装
  ↓
豆包大模型生成答案
  ↓
写入 RAG 缓存
```

---

## 2. 技术栈

- Java 17+
- Spring Boot 3.3.x
- Maven
- WebClient
- Caffeine 本地缓存
- Milvus REST API
- 豆包 / 火山方舟 OpenAI-compatible Chat API
- BGE-M3 OpenAI-compatible Embedding API
- bge-reranker-v2-m3 Rerank API

项目没有强依赖 Spring AI，因为 BGE 和 Milvus 的接口差异较大，直接用 WebClient 更容易适配已有 API。如果你后续统一使用 Spring AI，也可以把 `BgeEmbeddingClient` 和 `DoubaoClient` 替换成 Spring AI Client。

---

## 3. 项目结构

```text
game-rag-online/
├─ pom.xml
├─ Dockerfile
├─ .env.example
├─ README.md
├─ docs/
│  └─ api.http
├─ src/main/resources/
│  └─ application.yml
├─ src/main/java/com/example/gamerag/
│  ├─ GameRagOnlineApplication.java
│  ├─ config/
│  │  ├─ RagProperties.java
│  │  └─ WebClientConfig.java
│  ├─ controller/
│  │  └─ RagController.java
│  ├─ dto/
│  │  ├─ CacheClearResponse.java
│  │  ├─ RagQueryRequest.java
│  │  ├─ RagQueryResponse.java
│  │  └─ ReferenceDto.java
│  ├─ exception/
│  │  ├─ GlobalExceptionHandler.java
│  │  └─ RagException.java
│  ├─ model/
│  │  ├─ IntentType.java
│  │  ├─ QueryAnalysis.java
│  │  ├─ RecallChannel.java
│  │  ├─ RetrievalPlan.java
│  │  ├─ RetrievalStrategy.java
│  │  └─ SearchHit.java
│  ├─ service/
│  │  ├─ BgeEmbeddingClient.java
│  │  ├─ BgeRerankerClient.java
│  │  ├─ DoubaoClient.java
│  │  ├─ MilvusRetrievalService.java
│  │  ├─ PromptBuilder.java
│  │  ├─ QueryUnderstandingService.java
│  │  ├─ RagCacheService.java
│  │  ├─ RagService.java
│  │  ├─ RerankService.java
│  │  ├─ RetrievalRouter.java
│  │  └─ RrfFusionService.java
│  └─ util/
│     ├─ JsonExtractUtils.java
│     └─ TextUtils.java
└─ src/test/java/com/example/gamerag/service/
   └─ RrfFusionServiceTest.java
```

---

## 4. 核心修改点

### 4.1 Embedding 已改为 BGE-M3

新增：

```text
src/main/java/com/example/gamerag/service/BgeEmbeddingClient.java
```

调用 OpenAI-compatible embedding 接口：

```http
POST {rag.embedding.base-url}{rag.embedding.endpoint-path}
Authorization: Bearer {BGE_API_KEY}
Content-Type: application/json

{
  "model": "BAAI/bge-m3",
  "input": "玩家问题或改写后的查询"
}
```

默认配置：

```yaml
rag:
  embedding:
    base-url: https://api.siliconflow.cn/v1
    model: BAAI/bge-m3
    endpoint-path: /embeddings
```

如果你用自建 vLLM/Xinference/OpenAI-compatible 服务，只需要把 `base-url`、`model`、`endpoint-path` 改成你的接口。

### 4.2 Rerank 已改为 bge-reranker-v2-m3

新增：

```text
src/main/java/com/example/gamerag/service/BgeRerankerClient.java
```

调用常见 rerank API 格式：

```http
POST {rag.reranker.base-url}{rag.reranker.endpoint-path}
Authorization: Bearer {BGE_RERANK_API_KEY}
Content-Type: application/json

{
  "model": "BAAI/bge-reranker-v2-m3",
  "query": "改写后的查询",
  "documents": ["候选片段1", "候选片段2"],
  "top_n": 20,
  "return_documents": false
}
```

支持解析以下常见返回格式：

```json
{
  "results": [
    {"index": 0, "relevance_score": 0.92},
    {"index": 1, "relevance_score": 0.76}
  ]
}
```

也兼容 `data` 数组、`score` 字段、`document_index` 字段等常见变体。

### 4.3 豆包不再负责 embedding/rerank

`DoubaoClient` 现在只保留：

```java
public String chat(String systemPrompt, String userPrompt)
```

用于：

- `QueryUnderstandingService`：Query 理解。
- `RagService`：最终答案生成。

---

## 5. 配置说明

配置文件：

```text
src/main/resources/application.yml
```

### 5.1 豆包配置

```yaml
rag:
  doubao:
    api-key: ${DOUBAO_API_KEY:}
    base-url: ${DOUBAO_BASE_URL:https://ark.cn-beijing.volces.com/api/v3}
    chat-model: ${DOUBAO_CHAT_MODEL:ep-your-doubao-chat-endpoint}
    timeout: 60s
    temperature: 0.2
    max-tokens: 1200
```

环境变量示例：

```bash
export DOUBAO_API_KEY="你的豆包APIKey"
export DOUBAO_CHAT_MODEL="你的豆包模型接入点ID"
```

### 5.2 BGE-M3 Embedding 配置

```yaml
rag:
  embedding:
    api-key: ${BGE_API_KEY:}
    require-api-key: ${BGE_REQUIRE_API_KEY:true}
    base-url: ${BGE_BASE_URL:https://api.siliconflow.cn/v1}
    model: ${BGE_EMBEDDING_MODEL:BAAI/bge-m3}
    endpoint-path: ${BGE_EMBEDDING_PATH:/embeddings}
    timeout: 60s
    normalize-query-vector: ${BGE_NORMALIZE_QUERY_VECTOR:false}
    encoding-format: ${BGE_ENCODING_FORMAT:}
    dimensions: ${BGE_DIMENSIONS:0}
```

如果你的 BGE 服务是本地部署且没有鉴权：

```yaml
rag:
  embedding:
    require-api-key: false
```

或者：

```bash
export BGE_REQUIRE_API_KEY=false
```

### 5.3 bge-reranker 配置

```yaml
rag:
  reranker:
    enabled: ${BGE_RERANK_ENABLED:true}
    api-key: ${BGE_RERANK_API_KEY:${BGE_API_KEY:}}
    require-api-key: ${BGE_RERANK_REQUIRE_API_KEY:true}
    base-url: ${BGE_RERANK_BASE_URL:${BGE_BASE_URL:https://api.siliconflow.cn/v1}}
    model: ${BGE_RERANK_MODEL:BAAI/bge-reranker-v2-m3}
    endpoint-path: ${BGE_RERANK_PATH:/rerank}
    timeout: 60s
    max-document-chars: 1200
    return-documents: false
    fallback-to-heuristic: true
    rerank-weight: 0.85
```

`rerank-weight` 表示最终分数融合方式：

```text
final_score = rerank_weight * bge_rerank_score + (1 - rerank_weight) * normalized_rrf_score
```

默认 0.85，表示主要相信 bge-reranker，但保留少量 RRF 召回排序信号。

### 5.4 Milvus 配置

```yaml
rag:
  milvus:
    endpoint: ${MILVUS_ENDPOINT:http://localhost:19530}
    token: ${MILVUS_TOKEN:root:Milvus}
    database: ${MILVUS_DATABASE:default}
    collection: ${MILVUS_COLLECTION:game_knowledge}

    primary-key-field: ${MILVUS_ID_FIELD:id}
    text-field: ${MILVUS_TEXT_FIELD:content}
    title-field: ${MILVUS_TITLE_FIELD:title}
    source-field: ${MILVUS_SOURCE_FIELD:source}
    url-field: ${MILVUS_URL_FIELD:url}
    game-field: ${MILVUS_GAME_FIELD:game_id}
    dense-vector-field: ${MILVUS_DENSE_FIELD:vector}
    sparse-vector-field: ${MILVUS_SPARSE_FIELD:text_sparse}
```

你已有 Milvus schema 时，通常只需要改这些字段名。

---

## 6. 启动方式

### 6.1 本地启动

```bash
cp .env.example .env
# 按你的真实配置修改 .env
set -a
source .env
set +a

mvn clean package
java -jar target/game-rag-online-1.0.0.jar
```

### 6.2 Docker 启动

```bash
docker build -t game-rag-online:1.0 .
docker run --rm -p 8080:8080 \
  --env-file .env \
  game-rag-online:1.0
```

---

## 7. API 使用

### 7.1 RAG 问答

```http
POST http://localhost:8080/api/rag/query
Content-Type: application/json

{
  "query": "这个boss第二阶段怎么打？",
  "gameId": "demo_game",
  "topK": 6,
  "debug": true
}
```

返回示例：

```json
{
  "query": "这个boss第二阶段怎么打？",
  "answer": "……",
  "cacheHit": false,
  "references": [
    {
      "index": 1,
      "id": "chunk_001",
      "title": "Boss 第二阶段机制",
      "source": "wiki",
      "score": 0.91,
      "snippet": "……"
    }
  ],
  "debug": {
    "retrievalPlan": {
      "denseVector": true,
      "sparseBm25": true,
      "keywordQuery": true
    },
    "rawHitCount": 53,
    "fusedHitCount": 40,
    "latencyMs": 1520
  }
}
```

### 7.2 清空 RAG 缓存

```http
POST http://localhost:8080/api/rag/cache/clear
```

---

## 8. 与离线索引的适配要求

### 8.1 Dense 向量必须一致

线上查询使用：

```text
BAAI/bge-m3
```

因此离线入库的 `vector` 字段也应该由同一个 BGE-M3 模型生成。否则会出现：

- 检索语义不准；
- 向量维度不一致导致 Milvus 报错；
- COSINE/IP 距离分布不匹配。

### 8.2 Sparse/BM25 召回可选

当前代码保留了三路召回：

- Dense vector search；
- Sparse/BM25 search；
- Keyword TEXT_MATCH query。

如果你的 Milvus 没有 `text_sparse` 字段，关闭：

```yaml
rag:
  milvus:
    sparse-search-enabled: false
```

如果你的 `content` 字段没有启用 analyzer，关闭：

```yaml
rag:
  milvus:
    keyword-query-enabled: false
```

### 8.3 outputFields 必须存在

`application.yml` 默认：

```yaml
output-fields:
  - id
  - title
  - content
  - source
  - url
  - game_id
  - doc_type
  - version
```

如果你的集合没有 `doc_type` 或 `version`，请删掉，避免查询时报字段不存在。

---

## 9. 关键类说明

### 9.1 `BgeEmbeddingClient`

负责调用 BGE-M3 embedding API，返回 `List<Float>` query vector。

被 `MilvusRetrievalService` 调用：

```text
query -> BGE-M3 vector -> Milvus dense search
```

### 9.2 `BgeRerankerClient`

负责调用 bge-reranker API，输入 query + documents，输出 index + score。

### 9.3 `RerankService`

负责把 RRF 融合后的候选片段送入 bge-reranker，并把精排分数与 RRF 分数融合。

同时会把调试字段写入 `SearchHit.metadata`：

```text
rerank_model
bge_rerank_raw_score
bge_rerank_normalized_score
rrf_score_before_rerank
```

### 9.4 `MilvusRetrievalService`

负责：

- dense vector search；
- sparse/BM25 search；
- keyword query；
- Milvus 返回结果解析。

### 9.5 `QueryUnderstandingService`

使用豆包输出结构化 JSON：

```json
{
  "intent": "BOSS_STRATEGY",
  "strategy": "HYBRID",
  "rewrittenQuery": "……",
  "expandedQueries": ["……"],
  "keywords": ["……"],
  "entities": {"boss": "……"},
  "filters": {"doc_type": "guide"}
}
```

LLM 调用失败时会降级到规则解析。

---

## 10. 常见问题

### Q1：为什么切换 BGE-M3 后 Milvus 报维度错误？

说明离线索引向量维度和线上 BGE-M3 输出维度不一致。必须保证离线、线上使用同一个 embedding 模型和同一种输出设置。

### Q2：bge-reranker API 返回字段不是 `results/relevance_score` 怎么办？

`BgeRerankerClient` 已兼容：

- `results` 或 `data`；
- `index`、`document_index`、`doc_index`；
- `relevance_score`、`score`、`similarity`、`value`。

如果你的服务返回格式完全不同，只需要修改：

```text
BgeRerankerClient.parseScores()
```

### Q3：BGE API 和豆包 API 是同一个 Key 吗？

不一定。本项目分别配置：

```text
DOUBAO_API_KEY
BGE_API_KEY
BGE_RERANK_API_KEY
```

如果 embedding 和 rerank 用同一个 BGE 服务，可以只配 `BGE_API_KEY`，`BGE_RERANK_API_KEY` 会默认复用它。

### Q4：我用本地 vLLM/Xinference 部署 BGE，没 API Key 怎么配置？

```yaml
rag:
  embedding:
    require-api-key: false
  reranker:
    require-api-key: false
```

并把：

```yaml
base-url: http://localhost:8000/v1
```

改为你的本地服务地址。

### Q5：BGE-M3 是否会自动生成 sparse vector？

本项目默认只使用 BGE-M3 的 dense embedding 接口做线上向量化。Milvus sparse/BM25 召回沿用你离线建表时的 sparse/BM25 字段或 Milvus analyzer 能力。如果你希望用 BGE-M3 的 sparse 权重作为查询 sparse vector，需要你的 API 返回 sparse weights，并扩展 `BgeEmbeddingClient` 与 `MilvusRetrievalService.sparseSearch()`。

---

## 11. 推荐调参

初始推荐：

```yaml
rag:
  retrieval:
    fused-top-k: 40
    rerank-candidate-k: 20
    final-top-k: 6

  reranker:
    max-document-chars: 1200
    rerank-weight: 0.85
```

如果游戏知识片段较短：

```yaml
rerank-candidate-k: 30
max-document-chars: 800
```

如果每个 chunk 很长：

```yaml
rerank-candidate-k: 12
max-document-chars: 1800
```

如果 bge-reranker 延迟高：

```yaml
rerank-candidate-k: 10
```

---

## 12. 最小环境变量模板

```bash
export DOUBAO_API_KEY="你的豆包APIKey"
export DOUBAO_CHAT_MODEL="你的豆包接入点ID"

export BGE_API_KEY="你的BGE API Key"
export BGE_BASE_URL="https://api.siliconflow.cn/v1"
export BGE_EMBEDDING_MODEL="BAAI/bge-m3"
export BGE_RERANK_MODEL="BAAI/bge-reranker-v2-m3"

export MILVUS_ENDPOINT="http://localhost:19530"
export MILVUS_TOKEN="root:Milvus"
export MILVUS_COLLECTION="game_knowledge"
```

启动：

```bash
mvn clean package
java -jar target/game-rag-online-1.0.0.jar
```
