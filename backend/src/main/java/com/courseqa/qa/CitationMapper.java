package com.courseqa.qa;

import com.courseqa.contracts.dto.AnswerBlock;
import com.courseqa.contracts.dto.Citation;
import com.courseqa.contracts.dto.SearchHit;
import com.courseqa.contracts.dto.SearchResult;
import com.courseqa.contracts.internal.ModelAnswer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CitationMapper {
    record Snapshot(int citationId, UUID documentId, UUID chunkId, String fileName, int pageNo, String excerpt) {
        Citation display(boolean available) {
            return new Citation(citationId, documentId, chunkId, fileName, pageNo, excerpt, available);
        }
    }
    record BoundAnswer(List<AnswerBlock> blocks, List<Snapshot> citations) {}
    private final ConversationService scopes;

    public CitationMapper(ConversationService scopes) { this.scopes = scopes; }

    public List<Citation> map(ModelAnswer answer, SearchResult evidence) {
        var snapshots = bind(answer, evidence).citations();
        var available = scopes.availability(snapshots.stream().map(Snapshot::documentId).distinct().toList());
        return snapshots.stream().map(c -> c.display(Boolean.TRUE.equals(available.get(c.documentId())))).toList();
    }

    BoundAnswer bind(ModelAnswer answer, SearchResult evidence) {
        var sources = new HashMap<String, SearchHit>();
        evidence.hits().forEach(hit -> sources.put(hit.sourceId(), hit));
        var byChunk = new LinkedHashMap<UUID, Snapshot>();
        var blocks = new ArrayList<AnswerBlock>();
        for (var block : answer.answerBlocks()) {
            var numbers = new LinkedHashSet<Integer>();
            for (var sourceId : block.sourceIds()) {
                var hit = sources.get(sourceId);
                var citation = byChunk.computeIfAbsent(hit.chunkId(), id -> new Snapshot(byChunk.size() + 1,
                        hit.documentId(), id, hit.fileName(), hit.pageNo(), hit.text()));
                numbers.add(citation.citationId());
            }
            blocks.add(new AnswerBlock(block.text(), List.copyOf(numbers)));
        }
        return new BoundAnswer(List.copyOf(blocks), List.copyOf(byChunk.values()));
    }
}
