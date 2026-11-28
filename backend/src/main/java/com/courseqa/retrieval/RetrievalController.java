package com.courseqa.retrieval;

import com.courseqa.contracts.dto.SearchResult;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.port.Retriever;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RetrievalController {
    private final Retriever retriever;
    private final SearchRequestReader requests = new SearchRequestReader();

    public RetrievalController(Retriever retriever) {
        this.retriever = retriever;
    }

    @PostMapping(value = "/retrieval/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResult> search(@RequestBody String body) {
        Deadline deadline = new Deadline(System.nanoTime() + RetrievalRules.SEARCH_NANOS);
        return ResponseEntity.ok(retriever.search(requests.read(body), deadline));
    }
}
