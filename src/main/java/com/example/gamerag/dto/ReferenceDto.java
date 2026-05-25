package com.example.gamerag.dto;
// 答案引用的知识片段
public class ReferenceDto {
    private int index;
    private String id;
    private String title;
    private String source;
    private String url;
    private double score;
    private String snippet;

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
