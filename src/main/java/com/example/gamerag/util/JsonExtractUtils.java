package com.example.gamerag.util;
// JSON 提取工具
public final class JsonExtractUtils {
    private JsonExtractUtils() {}
// 从大模型输出中提取第一个 JSON 对象。用在：QueryUnderstandingService
    public static String extractFirstJsonObject(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}
