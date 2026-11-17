// 负责人 B。业务未实现时明确返回 501，不返回虚构业务数据。
package com.courseqa.documents;
import com.courseqa.contracts.dto.*;
import com.courseqa.common.FeatureNotImplementedException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/v1")
public class KnowledgeBaseController {
    // 课程列表
    @GetMapping(value="/knowledge-bases")
    public ResponseEntity<KnowledgeBaseList> listKnowledgeBases(@RequestParam(value="page", defaultValue="1") Integer page, @RequestParam(value="page_size", defaultValue="20") Integer page_size) {
        throw new FeatureNotImplementedException("B","listKnowledgeBases");
    }
    // 创建课程
    @PostMapping(value="/knowledge-bases")
    public ResponseEntity<KnowledgeBase> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreate body) {
        throw new FeatureNotImplementedException("B","createKnowledgeBase");
    }
    // 读取课程
    @GetMapping(value="/knowledge-bases/{knowledge_base_id}")
    public ResponseEntity<KnowledgeBase> getKnowledgeBase(@PathVariable(value="knowledge_base_id") UUID knowledge_base_id) {
        throw new FeatureNotImplementedException("B","getKnowledgeBase");
    }
    // 修改名称与说明 两字段均必传
    @PatchMapping(value="/knowledge-bases/{knowledge_base_id}")
    public ResponseEntity<KnowledgeBase> updateKnowledgeBase(@PathVariable(value="knowledge_base_id") UUID knowledge_base_id, @Valid @RequestBody KnowledgeBaseUpdate body) {
        throw new FeatureNotImplementedException("B","updateKnowledgeBase");
    }
    // 仅删除空课程
    @DeleteMapping(value="/knowledge-bases/{knowledge_base_id}")
    public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable(value="knowledge_base_id") UUID knowledge_base_id) {
        throw new FeatureNotImplementedException("B","deleteKnowledgeBase");
    }
}
