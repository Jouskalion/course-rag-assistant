package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.SearchRequest;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 在 HTTP 边界保留字段是否出现的信息，不手改生成 DTO 或全局 JSON 配置。 */
final class SearchRequestReader {
    private static final Set<String> FIELDS = Set.of("knowledge_base_id", "document_ids", "query", "strategy", "top_k");
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    SearchRequest read(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) {
                throw RetrievalRules.invalid("检索请求必须是 JSON 对象");
            }
            for (var field : root.properties()) {
                if (!FIELDS.contains(field.getKey())) {
                    throw RetrievalRules.invalid("检索请求包含未声明的字段");
                }
            }
            UUID courseId = uuid(root.get("knowledge_base_id"));
            JsonNode documents = root.get("document_ids");
            if (documents == null || !documents.isArray()) {
                throw RetrievalRules.invalid("document_ids 必须是资料编号数组");
            }
            if (documents.isEmpty()) {
                throw new ServiceException("EMPTY_SCOPE", 422, "请选择至少一份资料", false);
            }
            if (documents.size() > 100) {
                throw RetrievalRules.invalid("一次检索最多选择 100 份资料");
            }
            var ids = new ArrayList<UUID>();
            for (JsonNode id : documents) {
                ids.add(uuid(id));
            }
            int topK = RetrievalRules.DEFAULT_TOP_K;
            if (root.has("top_k")) {
                JsonNode value = root.get("top_k");
                if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                    throw RetrievalRules.invalid("top_k 必须为整数，不能为 null 或字符串");
                }
                topK = value.intValue();
            }
            return RetrievalRules.validate(new SearchRequest(courseId, ids, string(root.get("query")),
                    string(root.get("strategy")), topK));
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RetrievalRules.invalid("检索请求的 JSON、UUID 或字段格式不符合约定");
        }
    }

    private static String string(JsonNode value) {
        if (value == null || !value.isString()) {
            throw RetrievalRules.invalid("课程、资料、问题和策略字段必须具有约定的类型");
        }
        return value.asString();
    }

    private static UUID uuid(JsonNode value) {
        String text = string(value);
        UUID id = UUID.fromString(text);
        if (!id.toString().equals(text)) {
            throw RetrievalRules.invalid("编号必须为标准小写 UUID");
        }
        return id;
    }
}
