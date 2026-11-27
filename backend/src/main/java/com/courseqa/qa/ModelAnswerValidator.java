package com.courseqa.qa;

import com.courseqa.common.ModelJson;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.SearchHit;
import com.courseqa.contracts.dto.SearchResult;
import com.courseqa.contracts.internal.ModelAnswer;
import com.courseqa.contracts.internal.ModelBlock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ModelAnswerValidator {
    public ModelAnswer validate(String content, SearchResult evidence) {
        JsonNode root = ModelJson.parse(content);
        ModelJson.fields(root, "answer_status", "answer_blocks", "message");
        String status = ModelJson.nonblank(root.get("answer_status"), "answer_status");
        JsonNode blocks = root.get("answer_blocks");
        if (!blocks.isArray()) throw ModelJson.invalid("answer_blocks 必须是数组");
        if (Set.of("insufficient_evidence", "needs_clarification").contains(status)) {
            if (!blocks.isEmpty()) throw ModelJson.invalid("资料不足或需要澄清时 answer_blocks 必须为空");
            return new ModelAnswer(status, List.of(), ModelJson.nonblank(root.get("message"), "message"));
        }
        if (!"answered".equals(status) || blocks.isEmpty() || !root.get("message").isNull()) {
            throw ModelJson.invalid("answered 要求非空回答和 null 提示，且只能使用约定的状态");
        }
        var sources = evidence.hits().stream().map(SearchHit::sourceId).collect(Collectors.toSet());
        var parsed = new ArrayList<ModelBlock>();
        for (JsonNode block : blocks) {
            ModelJson.fields(block, "text", "source_ids");
            String text = ModelJson.nonblank(block.get("text"), "text");
            JsonNode ids = block.get("source_ids");
            if (!ids.isArray() || ids.isEmpty()) throw citationError("回答段落必须绑定本轮来源");
            var used = new LinkedHashSet<String>();
            for (JsonNode id : ids) {
                if (!id.isString() || !sources.contains(id.asString())) {
                    throw citationError("回答段落含有本轮证据之外的来源编号");
                }
                used.add(id.asString());
            }
            parsed.add(new ModelBlock(text, List.copyOf(used)));
        }
        return new ModelAnswer(status, parsed, null);
    }

    private static ServiceException citationError(String message) {
        return new ServiceException("CITATION_INVALID", 502, message, true);
    }
}
