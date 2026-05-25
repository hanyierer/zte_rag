package com.example.gamerag.config;
// 配置映射类
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Cache cache = new Cache();
    private Doubao doubao = new Doubao();
    private Embedding embedding = new Embedding();
    private Reranker reranker = new Reranker();
    private Milvus milvus = new Milvus();
    private QueryUnderstanding queryUnderstanding = new QueryUnderstanding();
    private Retrieval retrieval = new Retrieval();
    private Prompt prompt = new Prompt();

    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }
    public Doubao getDoubao() { return doubao; }
    public void setDoubao(Doubao doubao) { this.doubao = doubao; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Reranker getReranker() { return reranker; }
    public void setReranker(Reranker reranker) { this.reranker = reranker; }
    public Milvus getMilvus() { return milvus; }
    public void setMilvus(Milvus milvus) { this.milvus = milvus; }
    public QueryUnderstanding getQueryUnderstanding() { return queryUnderstanding; }
    public void setQueryUnderstanding(QueryUnderstanding queryUnderstanding) { this.queryUnderstanding = queryUnderstanding; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    public Prompt getPrompt() { return prompt; }
    public void setPrompt(Prompt prompt) { this.prompt = prompt; }

    public static class Cache {
        private boolean enabled = true;
        private long maxSize = 5000;
        private Duration ttl = Duration.ofMinutes(30);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }

    public static class Doubao {
        private String apiKey = "";
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        private String chatModel = "ep-your-doubao-chat-endpoint";
        private Duration timeout = Duration.ofSeconds(60);
        private double temperature = 0.2;
        private int maxTokens = 1200;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return trimTrailingSlash(baseUrl); }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getChatModel() { return chatModel; }
        public void setChatModel(String chatModel) { this.chatModel = chatModel; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    public static class Embedding {
        /**
         * 推荐 BAAI/bge-m3：中英文与多语言表现稳定，适合游戏百科、攻略、补丁说明混合语料。
         * baseUrl 一般填写到 /v1，例如 https://api.siliconflow.cn/v1 或你的自建 vLLM/Xinference OpenAI-compatible 地址。
         */
        private String apiKey = "";
        private boolean requireApiKey = true;
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String model = "BAAI/bge-m3";
        private String endpointPath = "/embeddings";
        private Duration timeout = Duration.ofSeconds(60);
        private boolean normalizeQueryVector = false;
        private String encodingFormat = "";
        private int dimensions = 0;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public boolean isRequireApiKey() { return requireApiKey; }
        public void setRequireApiKey(boolean requireApiKey) { this.requireApiKey = requireApiKey; }
        public String getBaseUrl() { return trimTrailingSlash(baseUrl); }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEndpointPath() { return normalizePath(endpointPath); }
        public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public boolean isNormalizeQueryVector() { return normalizeQueryVector; }
        public void setNormalizeQueryVector(boolean normalizeQueryVector) { this.normalizeQueryVector = normalizeQueryVector; }
        public String getEncodingFormat() { return encodingFormat; }
        public void setEncodingFormat(String encodingFormat) { this.encodingFormat = encodingFormat; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }

    public static class Reranker {
        /**
         * 推荐 BAAI/bge-reranker-v2-m3：BGE-M3 系列跨编码器精排模型，适合中英文/多语言场景。
         */
        private boolean enabled = true;
        private String apiKey = "";
        private boolean requireApiKey = true;
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String model = "BAAI/bge-reranker-v2-m3";
        private String endpointPath = "/rerank";
        private Duration timeout = Duration.ofSeconds(60);
        private int maxDocumentChars = 1200;
        private boolean returnDocuments = false;
        private boolean fallbackToHeuristic = true;
        /** Reranker 分数与 RRF/召回分数融合权重，0.85 表示以精排为主，保留少量召回信号。 */
        private double rerankWeight = 0.85;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public boolean isRequireApiKey() { return requireApiKey; }
        public void setRequireApiKey(boolean requireApiKey) { this.requireApiKey = requireApiKey; }
        public String getBaseUrl() { return trimTrailingSlash(baseUrl); }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEndpointPath() { return normalizePath(endpointPath); }
        public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getMaxDocumentChars() { return maxDocumentChars; }
        public void setMaxDocumentChars(int maxDocumentChars) { this.maxDocumentChars = maxDocumentChars; }
        public boolean isReturnDocuments() { return returnDocuments; }
        public void setReturnDocuments(boolean returnDocuments) { this.returnDocuments = returnDocuments; }
        public boolean isFallbackToHeuristic() { return fallbackToHeuristic; }
        public void setFallbackToHeuristic(boolean fallbackToHeuristic) { this.fallbackToHeuristic = fallbackToHeuristic; }
        public double getRerankWeight() { return rerankWeight; }
        public void setRerankWeight(double rerankWeight) { this.rerankWeight = rerankWeight; }
    }

    public static class Milvus {
        private String endpoint = "http://localhost:19530";
        private String token = "root:Milvus";
        private String database = "default";
        private String collection = "game_knowledge";
        private String primaryKeyField = "id";
        private String textField = "content";
        private String titleField = "title";
        private String sourceField = "source";
        private String urlField = "url";
        private String gameField = "game_id";
        private String denseVectorField = "vector";
        private String sparseVectorField = "text_sparse";
        private List<String> outputFields = new ArrayList<>(List.of("id", "title", "content", "source", "url", "game_id"));
        private boolean denseSearchEnabled = true;
        private boolean sparseSearchEnabled = true;
        private boolean keywordQueryEnabled = true;
        private String metricType = "COSINE";
        private String consistencyLevel = "Bounded";
        private int denseTopK = 30;
        private int sparseTopK = 30;
        private int keywordTopK = 30;
        private String keywordFilterTemplate = "TEXT_MATCH({textField}, '{keyword}')";
        public String getEndpoint() { return trimTrailingSlash(endpoint); }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public String getPrimaryKeyField() { return primaryKeyField; }
        public void setPrimaryKeyField(String primaryKeyField) { this.primaryKeyField = primaryKeyField; }
        public String getTextField() { return textField; }
        public void setTextField(String textField) { this.textField = textField; }
        public String getTitleField() { return titleField; }
        public void setTitleField(String titleField) { this.titleField = titleField; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
        public String getUrlField() { return urlField; }
        public void setUrlField(String urlField) { this.urlField = urlField; }
        public String getGameField() { return gameField; }
        public void setGameField(String gameField) { this.gameField = gameField; }
        public String getDenseVectorField() { return denseVectorField; }
        public void setDenseVectorField(String denseVectorField) { this.denseVectorField = denseVectorField; }
        public String getSparseVectorField() { return sparseVectorField; }
        public void setSparseVectorField(String sparseVectorField) { this.sparseVectorField = sparseVectorField; }
        public List<String> getOutputFields() { return outputFields; }
        public void setOutputFields(List<String> outputFields) { this.outputFields = outputFields; }
        public boolean isDenseSearchEnabled() { return denseSearchEnabled; }
        public void setDenseSearchEnabled(boolean denseSearchEnabled) { this.denseSearchEnabled = denseSearchEnabled; }
        public boolean isSparseSearchEnabled() { return sparseSearchEnabled; }
        public void setSparseSearchEnabled(boolean sparseSearchEnabled) { this.sparseSearchEnabled = sparseSearchEnabled; }
        public boolean isKeywordQueryEnabled() { return keywordQueryEnabled; }
        public void setKeywordQueryEnabled(boolean keywordQueryEnabled) { this.keywordQueryEnabled = keywordQueryEnabled; }
        public String getMetricType() { return metricType; }
        public void setMetricType(String metricType) { this.metricType = metricType; }
        public String getConsistencyLevel() { return consistencyLevel; }
        public void setConsistencyLevel(String consistencyLevel) { this.consistencyLevel = consistencyLevel; }
        public int getDenseTopK() { return denseTopK; }
        public void setDenseTopK(int denseTopK) { this.denseTopK = denseTopK; }
        public int getSparseTopK() { return sparseTopK; }
        public void setSparseTopK(int sparseTopK) { this.sparseTopK = sparseTopK; }
        public int getKeywordTopK() { return keywordTopK; }
        public void setKeywordTopK(int keywordTopK) { this.keywordTopK = keywordTopK; }
        public String getKeywordFilterTemplate() { return keywordFilterTemplate; }
        public void setKeywordFilterTemplate(String keywordFilterTemplate) { this.keywordFilterTemplate = keywordFilterTemplate; }
    }

    public static class QueryUnderstanding {
        private boolean enabled = true;
        private int maxExpandedQueries = 4;
        private boolean fallbackToRules = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxExpandedQueries() { return maxExpandedQueries; }
        public void setMaxExpandedQueries(int maxExpandedQueries) { this.maxExpandedQueries = maxExpandedQueries; }
        public boolean isFallbackToRules() { return fallbackToRules; }
        public void setFallbackToRules(boolean fallbackToRules) { this.fallbackToRules = fallbackToRules; }
    }

    public static class Retrieval {
        private int rrfK = 60;
        private int fusedTopK = 40;
        private int rerankCandidateK = 20;
        private int finalTopK = 6;
        private double minScore = 0.0;
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
        public int getFusedTopK() { return fusedTopK; }
        public void setFusedTopK(int fusedTopK) { this.fusedTopK = fusedTopK; }
        public int getRerankCandidateK() { return rerankCandidateK; }
        public void setRerankCandidateK(int rerankCandidateK) { this.rerankCandidateK = rerankCandidateK; }
        public int getFinalTopK() { return finalTopK; }
        public void setFinalTopK(int finalTopK) { this.finalTopK = finalTopK; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
    }

    public static class Prompt {
        private int maxContextChars = 9000;
        private int maxDocChars = 1200;
        private String systemMessage = "你是一个严谨的游戏知识库问答助手。";
        public int getMaxContextChars() { return maxContextChars; }
        public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }
        public int getMaxDocChars() { return maxDocChars; }
        public void setMaxDocChars(int maxDocChars) { this.maxDocChars = maxDocChars; }
        public String getSystemMessage() { return systemMessage; }
        public void setSystemMessage(String systemMessage) { this.systemMessage = systemMessage; }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) return "";
        return value.startsWith("/") ? value : "/" + value;
    }
}
