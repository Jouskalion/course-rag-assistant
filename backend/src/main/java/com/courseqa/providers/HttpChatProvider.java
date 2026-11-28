package com.courseqa.providers;

import com.courseqa.common.ModelJson;
import com.courseqa.common.ModelResponseException;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.SearchHit;
import com.courseqa.contracts.internal.*;
import com.courseqa.contracts.port.ChatProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class HttpChatProvider implements ChatProvider {
    private final ModelHttpClient client;
    private final String model;
    private final String answerPrompt;
    private final String repairPrompt;
    private final String rewritePrompt;

    public HttpChatProvider(ModelHttpClient client, @Value("${app.model.generation-model}") String model)
            throws IOException {
        this.client = client;
        this.model = model;
        answerPrompt = prompt("answer");
        repairPrompt = answerPrompt + "\n\n" + prompt("repair");
        rewritePrompt = prompt("rewrite");
    }

    private static String prompt(String name) throws IOException {
        return new ClassPathResource("prompts/" + name + ".txt").getContentAsString(StandardCharsets.UTF_8);
    }

    @Override public RewriteResult rewrite(String question, List<HistoryTurn> history, Deadline deadline) {
        ChatCall call = chat(rewritePrompt, Map.of("question", question, "history", history), 256, deadline);
        try {
            JsonNode root = ModelJson.parse(call.content());
            ModelJson.fields(root, "needs_clarification", "standalone_question", "message");
            JsonNode clarification = root.get("needs_clarification");
            if (!clarification.isBoolean()) throw ModelJson.invalid("needs_clarification 必须是布尔值");
            if (clarification.booleanValue()) {
                if (!root.get("standalone_question").isNull()) throw ModelJson.invalid("待澄清问题必须为 null");
                return new RewriteResult(true, null, ModelJson.nonblank(root.get("message"), "message"),
                        call.usage(), call.actualModel());
            }
            String standalone = ModelJson.nonblank(root.get("standalone_question"), "standalone_question").strip();
            if (!root.get("message").isNull() || standalone.codePointCount(0, standalone.length()) > 2000) {
                throw ModelJson.invalid("独立问题最多 2000 码点，成功改写时 message 必须为 null");
            }
            return new RewriteResult(false, standalone, null, call.usage(), call.actualModel());
        } catch (ServiceException ex) {
            throw new ModelResponseException(ex.getMessage(), call.usage(), call.actualModel());
        }
    }

    @Override public ChatCall generate(String question, List<SearchHit> evidence, Deadline deadline) {
        return chat(answerPrompt, input(question, evidence), 2048, deadline);
    }

    @Override public ChatCall repair(String question, List<SearchHit> evidence, String invalidOutput,
            String validationError, Deadline deadline) {
        var data = input(question, evidence);
        data.put("invalid_output", invalidOutput);
        data.put("validation_error", validationError);
        return chat(repairPrompt, data, 2048, deadline);
    }

    private static Map<String, Object> input(String question, List<SearchHit> evidence) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question", question);
        data.put("evidence", evidence.stream()
                .map(hit -> Map.of("source_id", hit.sourceId(), "text", hit.text())).toList());
        return data;
    }

    private ChatCall chat(String system, Map<String, ?> data, int budget, Deadline deadline) {
        var messages = List.of(Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", ModelJson.MAPPER.writeValueAsString(data)));
        String body = ModelJson.MAPPER.writeValueAsString(Map.of("model", model, "temperature", 0.1,
                "enable_thinking", false, "response_format", Map.of("type", "json_object"),
                "max_tokens", budget, "messages", messages));
        JsonNode root = ModelJson.parse(client.post("chat/completions", body, deadline));
        JsonNode counts = root.path("usage");
        var usage = new ProviderUsage(tokens(counts.path("prompt_tokens")), tokens(counts.path("completion_tokens")));
        JsonNode name = root.path("model");
        String actualModel = name.isString() && !name.asString().isBlank() ? name.asString() : null;
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (root.has("error") || !content.isString()) {
            throw new ModelResponseException("模型服务响应缺少回答内容", usage, actualModel);
        }
        return new ChatCall(content.asString(), usage, actualModel);
    }

    private static Long tokens(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return null;
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0
                ? value.longValue() : null;
    }
}
