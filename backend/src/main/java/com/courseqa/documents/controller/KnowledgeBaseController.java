// 负责人 B。B01：课程接口已接入真实实现，未实现的资料接口仍在其他控制器返回 501。
package com.courseqa.documents.controller;
import com.courseqa.common.ServiceException;
import com.courseqa.documents.service.KnowledgeBaseService;
import com.courseqa.contracts.dto.KnowledgeBase;
import com.courseqa.contracts.dto.KnowledgeBaseCreate;
import com.courseqa.contracts.dto.KnowledgeBaseList;
import com.courseqa.contracts.dto.KnowledgeBaseUpdate;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;
    private final Validator validator;

    public KnowledgeBaseController(KnowledgeBaseService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @GetMapping(value = "/knowledge-bases")
    public ResponseEntity<KnowledgeBaseList> listKnowledgeBases(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        return ResponseEntity.ok(service.list(page, pageSize));
    }

    @PostMapping(value = "/knowledge-bases")
    public ResponseEntity<KnowledgeBase> createKnowledgeBase(@RequestBody Map<String, Object> raw) {
        // 契约总则：可选字段省略时用默认值，显式 null 不等于省略。
        // description 在 DTO 中是 String，省略与显式 null 都会变成 Java null，
        // 因此在构造 DTO 前从原始 JSON 区分：键存在且值为 null 必须拒绝。
        if (raw.containsKey("description") && raw.get("description") == null) {
            throw invalid("description 显式为 null 不等于省略");
        }
        Object nameValue = raw.get("name");
        Object descriptionValue = raw.get("description");
        if (nameValue != null && !(nameValue instanceof String)) throw invalid("name 必须是字符串");
        if (descriptionValue != null && !(descriptionValue instanceof String)) throw invalid("description 必须是字符串");
        for (String key : raw.keySet()) {
            if (!"name".equals(key) && !"description".equals(key)) throw invalid("存在未知字段: " + key);
        }
        KnowledgeBaseCreate body = new KnowledgeBaseCreate((String) nameValue, (String) descriptionValue);
        validate(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
    }

    @GetMapping(value = "/knowledge-bases/{knowledge_base_id}")
    public ResponseEntity<KnowledgeBase> getKnowledgeBase(@PathVariable(value = "knowledge_base_id") UUID knowledgeBaseId) {
        return ResponseEntity.ok(service.get(knowledgeBaseId));
    }

    @PatchMapping(value = "/knowledge-bases/{knowledge_base_id}")
    public ResponseEntity<KnowledgeBase> updateKnowledgeBase(
            @PathVariable(value = "knowledge_base_id") UUID knowledgeBaseId,
            @Valid @RequestBody KnowledgeBaseUpdate body) {
        return ResponseEntity.ok(service.update(knowledgeBaseId, body));
    }

    @DeleteMapping(value = "/knowledge-bases/{knowledge_base_id}")
    public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable(value = "knowledge_base_id") UUID knowledgeBaseId) {
        service.delete(knowledgeBaseId);
        return ResponseEntity.noContent().build();
    }

    private <T> void validate(T body) {
        // 与 @Valid 触发 MethodArgumentNotValidException 时的响应保持一致。
        if (!validator.validate(body).isEmpty()) throw invalid("请求字段或格式不符合接口约定");
    }

    private ServiceException invalid(String message) {
        return new ServiceException("INVALID_REQUEST", 422, message, false);
    }
}
