package com.courseqa.qa;

import com.courseqa.contracts.dto.Conversation;
import com.courseqa.contracts.dto.ConversationList;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) { this.service = service; }

    @GetMapping("/conversations")
    public ConversationList listConversations(@RequestParam MultiValueMap<String,String> query) {
        var page = QaRequests.page(query, true);
        return service.list(QaRequests.uuid(query.getFirst("knowledge_base_id")), page);
    }

    @PostMapping(value="/conversations", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Conversation> createConversation(@RequestBody String body) {
        return ResponseEntity.status(201).body(service.create(QaRequests.conversation(body)));
    }

    @GetMapping("/conversations/{conversation_id}")
    public Conversation getConversation(@PathVariable("conversation_id") String id) {
        return service.get(QaRequests.uuid(id));
    }

    @PatchMapping(value="/conversations/{conversation_id}", consumes=MediaType.APPLICATION_JSON_VALUE)
    public Conversation updateConversation(@PathVariable("conversation_id") String id, @RequestBody String body) {
        return service.rename(QaRequests.uuid(id), QaRequests.rename(body).title());
    }

    @DeleteMapping("/conversations/{conversation_id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable("conversation_id") String id) {
        service.delete(QaRequests.uuid(id));
        return ResponseEntity.noContent().build();
    }
}
