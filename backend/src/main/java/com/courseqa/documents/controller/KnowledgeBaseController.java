// 负责人 B。B01：课程接口已接入真实实现，未实现的资料接口仍在其他控制器返回 501。
package com.courseqa.documents.controller;
import com.courseqa.documents.service.KnowledgeBaseService;
import com.courseqa.contracts.dto.KnowledgeBase;
import com.courseqa.contracts.dto.KnowledgeBaseCreate;
import com.courseqa.contracts.dto.KnowledgeBaseList;
import com.courseqa.contracts.dto.KnowledgeBaseUpdate;
import jakarta.validation.Valid;
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

    public KnowledgeBaseController(KnowledgeBaseService service) { this.service = service; }

    @GetMapping(value = "/knowledge-bases")
    public ResponseEntity<KnowledgeBaseList> listKnowledgeBases(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        return ResponseEntity.ok(service.list(page, pageSize));
    }

    @PostMapping(value = "/knowledge-bases")
    public ResponseEntity<KnowledgeBase> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreate body) {
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
}
