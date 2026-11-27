package com.courseqa.qa;

import com.courseqa.contracts.dto.Feedback;
import com.courseqa.contracts.dto.Turn;
import com.courseqa.contracts.dto.TurnList;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TurnController {
    private final TurnService service;
    public TurnController(TurnService service) { this.service = service; }

    @GetMapping("/conversations/{conversation_id}/turns")
    public TurnList listTurns(@PathVariable("conversation_id") String id, @RequestParam MultiValueMap<String,String> query) {
        return service.list(QaRequests.uuid(id), QaRequests.page(query, false));
    }

    @PostMapping(value="/conversations/{conversation_id}/turns", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Turn> createTurn(@PathVariable("conversation_id") String id, @RequestBody String body) {
        return response(service.create(QaRequests.uuid(id), QaRequests.question(body)));
    }

    @GetMapping("/turns/{turn_id}")
    public Turn getTurn(@PathVariable("turn_id") String id) { return service.get(QaRequests.uuid(id)); }

    @PostMapping(value="/turns/{turn_id}/retry", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Turn> retryTurn(@PathVariable("turn_id") String id, @RequestBody String body) {
        return response(service.retry(QaRequests.uuid(id), QaRequests.retry(body).clientRequestId()));
    }

    @PutMapping(value="/turns/{turn_id}/feedback", consumes=MediaType.APPLICATION_JSON_VALUE)
    public Feedback putFeedback(@PathVariable("turn_id") String id, @RequestBody String body) {
        return service.feedback(QaRequests.uuid(id), QaRequests.feedback(body));
    }

    private ResponseEntity<Turn> response(TurnService.Submission submission) {
        return ResponseEntity.status(submission.created() ? 202 : 200).body(submission.turn());
    }
}
