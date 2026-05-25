package com.example.gamerag.util;
// 文本工具类
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TextUtils {
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private TextUtils() {}

    public static String normalizeQuery(String text) {
        if (text == null) return "";
        return SPACES.matcher(text.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    public static List<String> distinctNonBlank(List<String> input, int max) {
        Set<String> set = new LinkedHashSet<>();
        if (input != null) {
            for (String s : input) {
                if (s != null && !s.isBlank()) {
                    set.add(s.trim());
                }
            }
        }
        List<String> out = new ArrayList<>(set);
        return out.size() > max ? out.subList(0, max) : out;
    }

    public static String escapeMilvusString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }

    public static int keywordHitCount(String text, List<String> keywords) {
        if (text == null || keywords == null || keywords.isEmpty()) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }
}
