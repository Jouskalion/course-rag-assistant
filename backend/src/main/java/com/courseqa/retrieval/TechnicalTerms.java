package com.courseqa.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 索引和查询共用技术词规则，保留下划线、点号、连字符、C++ 与 C#。 */
final class TechnicalTerms {
    private static final Pattern WORD = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(?:[.-][A-Za-z0-9_]+)*(?:\\+\\+|#)?");

    private TechnicalTerms() {}

    static List<String> extract(String text) {
        List<String> words = new ArrayList<>();
        var matcher = WORD.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(words);
    }

    static String indexText(String text) {
        return String.join(" ", extract(text));
    }
}
