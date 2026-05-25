package com.example.gamerag.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
// 被检索到的一个知识片段
public class SearchHit {
    private String id;
    private String title;
    private String content;
    private String source;
    private String url;
    private double score;
    private int rank;
    private RecallChannel channel;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public SearchHit() {
    }

    public SearchHit(String id, String title, String content, String source, String url, double score, RecallChannel channel) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.source = source;
        this.url = url;
        this.score = score;
        this.channel = channel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public RecallChannel getChannel() { return channel; }
    public void setChannel(RecallChannel channel) { this.channel = channel; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : metadata; }

    public SearchHit copy() {
        SearchHit hit = new SearchHit(id, title, content, source, url, score, channel);
        hit.setRank(rank);
        hit.setMetadata(new LinkedHashMap<>(metadata));
        return hit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchHit searchHit)) return false;
        return Objects.equals(id, searchHit.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
