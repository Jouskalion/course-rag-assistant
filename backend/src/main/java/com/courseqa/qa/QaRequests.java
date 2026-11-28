package com.courseqa.qa;

import com.courseqa.common.ModelJson;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/** 保留可选字段省略与显式 null 的区别，校验完成后统一使用生成的 DTO。 */
final class QaRequests {
    private QaRequests() {}

    static ConversationCreate conversation(String body) {
        JsonNode root = object(body, Set.of("knowledge_base_id", "scope", "document_ids", "title"));
        var course = uuid(string(root.get("knowledge_base_id"), "knowledge_base_id"));
        var scope = choice(root.get("scope"), "scope", Set.of("all", "selected"));
        List<UUID> documents = null;
        if ("selected".equals(scope)) {
            var ids = root.get("document_ids");
            if (ids == null || !ids.isArray()) throw invalid("selected 需要 document_ids 数组");
            if (ids.isEmpty()) throw new ServiceException("EMPTY_SCOPE", 422, "请选择至少一份资料", false);
            if (ids.size() > 100) throw invalid("最多选择 100 份资料");
            var distinct = new LinkedHashSet<UUID>();
            for (JsonNode item : ids) {
                if (!distinct.add(uuid(string(item, "document_ids")))) throw invalid("资料编号不能重复");
            }
            documents = List.copyOf(distinct);
        } else if (root.has("document_ids")) {
            throw invalid("all 范围应省略 document_ids");
        }
        return new ConversationCreate(course, scope, documents, root.has("title") ? title(root) : "新会话");
    }

    static ConversationUpdate rename(String body) {
        return new ConversationUpdate(title(object(body, Set.of("title"))));
    }

    private static String title(JsonNode node) {
        return bounded(string(node.get("title"), "title").strip(), 1, 80, "title");
    }

    static TurnCreate question(String body) {
        JsonNode root = object(body, Set.of("client_request_id", "question", "strategy", "top_k"));
        String question = bounded(string(root.get("question"), "question").strip(), 1, 2000, "question");
        String strategy = root.has("strategy")
                ? choice(root.get("strategy"), "strategy", Set.of("bm25", "dense", "hybrid")) : "hybrid";
        int top = 6;
        if (root.has("top_k")) {
            var value = root.get("top_k");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > 10) {
                throw invalid("top_k 必须为 1 至 10 的整数");
            }
            top = value.intValue();
        }
        return new TurnCreate(uuid(string(root.get("client_request_id"), "client_request_id")), question, strategy, top);
    }

    static TurnRetry retry(String body) {
        var root = object(body, Set.of("client_request_id"));
        return new TurnRetry(uuid(string(root.get("client_request_id"), "client_request_id")));
    }

    static FeedbackWrite feedback(String body) {
        var root = object(body, Set.of("rating", "reason", "comment"));
        String rating = choice(root.get("rating"), "rating", Set.of("up", "down"));
        if (!root.has("reason")) throw invalid("反馈需要 reason 字段，可取 null");
        String reason = root.get("reason").isNull() ? null : choice(root.get("reason"), "reason",
                Set.of("helpful", "incorrect", "unsupported", "missing_information", "other"));
        return new FeedbackWrite(rating, reason, bounded(string(root.get("comment"), "comment"), 0, 1000, "comment"));
    }

    record Page(int number, int size) {
        long offset() { return (long) (number - 1) * size; }
    }

    static Page page(MultiValueMap<String, String> params, boolean withCourse) {
        var allowed = withCourse ? Set.of("page", "page_size", "knowledge_base_id") : Set.of("page", "page_size");
        if (params.entrySet().stream().anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue().size() != 1)) {
            throw invalid("分页参数含有重复或未声明的字段");
        }
        return new Page(positive(params.getFirst("page"), 1, Integer.MAX_VALUE),
                positive(params.getFirst("page_size"), 20, 100));
    }

    private static int positive(String raw, int fallback, int max) {
        if (raw == null) return fallback;
        try {
            int n = Integer.parseInt(raw);
            if (n >= 1 && n <= max) return n;
        } catch (NumberFormatException ignored) {}
        throw invalid("页码或每页数量超出范围");
    }

    static UUID uuid(String value) {
        try {
            var id = UUID.fromString(value);
            if (id.toString().equals(value)) return id;
        } catch (IllegalArgumentException | NullPointerException ignored) {}
        throw invalid("编号必须是标准小写 UUID");
    }

    private static JsonNode object(String body, Set<String> allowed) {
        try {
            var root = ModelJson.MAPPER.readTree(body);
            if (root != null && root.isObject() && allowed.containsAll(root.propertyNames())) return root;
        } catch (RuntimeException ignored) {}
        throw invalid("请求必须是单个 JSON 对象，只能包含约定的字段");
    }

    private static String choice(JsonNode value, String name, Set<String> choices) {
        String text = string(value, name);
        if (!choices.contains(text)) throw invalid(name + " 超出允许的枚举范围");
        return text;
    }

    private static String string(JsonNode value, String name) {
        if (value == null || !value.isString()) throw invalid(name + " 必须是字符串");
        return value.asString();
    }

    private static String bounded(String value, int min, int max, String name) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) throw invalid(name + " 长度应为 " + min + " 至 " + max + " 个码点");
        return value;
    }

    static ServiceException invalid(String message) { return new ServiceException("INVALID_REQUEST", 422, message, false); }
    static ServiceException missing(String kind) { return new ServiceException("NOT_FOUND", 404, kind + "不存在", false); }
    static ServiceException busy() { return new ServiceException("CONVERSATION_BUSY", 409, "此会话已有活动问题", false); }
}
