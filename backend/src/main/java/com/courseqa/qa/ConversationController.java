// 负责人 D。业务未实现时明确返回 501，不返回虚构业务数据。
package com.courseqa.qa;
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
public class ConversationController {
    // 按课程列出会话
    @GetMapping(value="/conversations")
    public ResponseEntity<ConversationList> listConversations(@RequestParam(value="page", defaultValue="1") Integer page, @RequestParam(value="page_size", defaultValue="20") Integer page_size, @RequestParam(value="knowledge_base_id") UUID knowledge_base_id) {
        throw new FeatureNotImplementedException("D","listConversations");
    }
    // 创建固定资料范围会话
    @PostMapping(value="/conversations")
    public ResponseEntity<Conversation> createConversation(@Valid @RequestBody ConversationCreate body) {
        throw new FeatureNotImplementedException("D","createConversation");
    }
    // 读取会话与失效资料
    @GetMapping(value="/conversations/{conversation_id}")
    public ResponseEntity<Conversation> getConversation(@PathVariable(value="conversation_id") UUID conversation_id) {
        throw new FeatureNotImplementedException("D","getConversation");
    }
    // 修改标题
    @PatchMapping(value="/conversations/{conversation_id}")
    public ResponseEntity<Conversation> updateConversation(@PathVariable(value="conversation_id") UUID conversation_id, @Valid @RequestBody ConversationUpdate body) {
        throw new FeatureNotImplementedException("D","updateConversation");
    }
    // 无活动问答时删除会话
    @DeleteMapping(value="/conversations/{conversation_id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable(value="conversation_id") UUID conversation_id) {
        throw new FeatureNotImplementedException("D","deleteConversation");
    }
}
