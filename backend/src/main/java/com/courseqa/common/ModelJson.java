package com.courseqa.common;

import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 严格读取模型生成的 JSON；用户只看到字段约束，不回显供应商原文。 */
public final class ModelJson {
    private ModelJson() {}
    public static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public static JsonNode parse(String content) {
        try {
            JsonNode node = MAPPER.readTree(content);
            if (node != null && node.isObject()) return node;
        } catch (RuntimeException ignored) {
            // The parser message can contain the response itself.
        }
        throw invalid("输出必须是单个 JSON 对象");
    }

    public static void fields(JsonNode object, String... names) {
        if (!object.isObject() || object.size() != names.length || !object.propertyNames().containsAll(Set.of(names))) {
            throw invalid("对象必须且只能包含 " + String.join("、", names));
        }
    }

    public static String nonblank(JsonNode node, String field) {
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw invalid(field + " 必须是非空字符串");
        }
        return node.asString();
    }

    public static ServiceException invalid(String reason) {
        return new ServiceException("MODEL_OUTPUT_INVALID", 502, reason, true);
    }
}
