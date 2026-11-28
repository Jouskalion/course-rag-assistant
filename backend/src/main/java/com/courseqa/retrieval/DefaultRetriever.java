package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.SearchHit;
import com.courseqa.contracts.dto.SearchRequest;
import com.courseqa.contracts.dto.SearchResult;
import com.courseqa.contracts.internal.CatalogChunk;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.internal.QueryVector;
import com.courseqa.contracts.port.EmbeddingProvider;
import com.courseqa.contracts.port.Retriever;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultRetriever implements Retriever {
    private final CatalogSnapshotRegistry snapshots;
    private final EmbeddingProvider embeddings;

    public DefaultRetriever(CatalogSnapshotRegistry snapshots, EmbeddingProvider embeddings) {
        this.snapshots = snapshots;
        this.embeddings = embeddings;
    }

    @Override
    public SearchResult search(SearchRequest request, Deadline deadline) {
        long started = System.nanoTime();
        RetrievalRules.checkDeadline(deadline);
        SearchRequest input = RetrievalRules.validate(request);
        Set<UUID> scope = Set.copyOf(input.documentIds());
        long embeddingNanos = 0;
        Integer inputTokens = 0;
        String actualModel = null;
        try (CatalogSnapshotLease lease = snapshots.acquire(input.knowledgeBaseId(), input.documentIds(), deadline)) {
            CatalogSnapshot snapshot = lease.snapshot();
            List<RankedChunk> bm25 = List.of();
            List<RankedChunk> dense = List.of();
            boolean hasChunks = snapshot.chunks().values().stream().anyMatch(chunk -> scope.contains(chunk.documentId()));
            if (hasChunks && !"dense".equals(input.strategy())) {
                bm25 = snapshot.bm25().search(input.query(), scope, deadline);
            }
            if (hasChunks && !"bm25".equals(input.strategy())) {
                RetrievalRules.checkDeadline(deadline);
                long embeddingStarted = System.nanoTime();
                QueryVector vector = embeddings.embedQuery(input.query(), deadline);
                embeddingNanos = System.nanoTime() - embeddingStarted;
                RetrievalRules.checkDeadline(deadline);
                if (vector == null) {
                    throw RetrievalRules.invalidVector();
                }
                inputTokens = usage(vector);
                actualModel = vector.actualModel();
                dense = snapshot.dense().search(vector, scope, deadline);
            }
            List<SearchHit> hits = select(RrfFusion.merge(input.strategy(), bm25, dense),
                    snapshot, scope, input.topK(), deadline);
            RetrievalRules.checkDeadline(deadline);
            double retrievalMs = Math.max(0, System.nanoTime() - started - embeddingNanos) / 1_000_000.0;
            return new SearchResult(input.query(), input.strategy(), input.documentIds(), input.topK(), hits,
                    embeddingNanos / 1_000_000.0, retrievalMs, inputTokens, actualModel);
        }
    }

    private static Integer usage(QueryVector query) {
        if (query.usage() == null || query.usage().inputTokens() == null) {
            return null;
        }
        long tokens = query.usage().inputTokens();
        if (tokens < 0 || tokens > Integer.MAX_VALUE) {
            throw new ServiceException("EMBEDDING_FAILED", 502, "查询向量用量不符合接口整数范围", true);
        }
        return (int) tokens;
    }

    private static List<SearchHit> select(List<RrfFusion.Candidate> candidates, CatalogSnapshot snapshot,
            Set<UUID> scope, int topK, Deadline deadline) {
        List<SearchHit> hits = new ArrayList<>();
        List<CatalogChunk> kept = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (RrfFusion.Candidate candidate : candidates) {
            RetrievalRules.checkDeadline(deadline);
            CatalogChunk chunk = snapshot.chunks().get(candidate.chunkId());
            if (chunk == null || !scope.contains(chunk.documentId())) {
                throw new ServiceException("INTERNAL_ERROR", 500, "检索候选与本轮资料快照不一致", false);
            }
            if (!seen.add(chunk.chunkId()) || kept.stream().anyMatch(previous -> overlaps(previous, chunk))) {
                continue;
            }
            kept.add(chunk);
            hits.add(new SearchHit("S" + (hits.size() + 1), chunk.chunkId(), chunk.documentId(), chunk.fileName(),
                    chunk.pageNo(), chunk.charStart(), chunk.charEnd(), chunk.text(),
                    candidate.bm25Rank(), candidate.denseRank(), candidate.bm25Score(), candidate.denseScore(),
                    candidate.rrfScore()));
            if (hits.size() == topK) {
                break;
            }
        }
        return List.copyOf(hits);
    }

    private static boolean overlaps(CatalogChunk a, CatalogChunk b) {
        if (!a.documentId().equals(b.documentId()) || a.pageNo() != b.pageNo()) {
            return false;
        }
        long intersection = Math.max(0, Math.min(a.charEnd(), b.charEnd()) - Math.max(a.charStart(), b.charStart()));
        long shorter = Math.min(a.charEnd() - a.charStart(), b.charEnd() - b.charStart());
        return intersection * 5 >= shorter * 4;
    }
}
