package com.courseqa.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 仅融合候选排名，BM25 与向量原始分数不混加。 */
final class RrfFusion {
    record Candidate(UUID chunkId, Integer bm25Rank, Integer denseRank,
            Double bm25Score, Double denseScore, Double rrfScore) {
        double score(String strategy) {
            return switch (strategy) {
                case "bm25" -> bm25Score;
                case "dense" -> denseScore;
                case "hybrid" -> rrfScore;
                default -> throw RetrievalRules.invalid("检索策略不合法");
            };
        }
    }

    private RrfFusion() {}

    static List<Candidate> merge(String strategy, List<RankedChunk> bm25, List<RankedChunk> dense) {
        Map<UUID, RankedChunk> keywords = byId(bm25);
        Map<UUID, RankedChunk> semantic = byId(dense);
        var ids = new java.util.HashSet<>(keywords.keySet());
        ids.addAll(semantic.keySet());
        List<Candidate> result = new ArrayList<>();
        for (UUID id : ids) {
            RankedChunk b = keywords.get(id);
            RankedChunk d = semantic.get(id);
            Double rrf = "hybrid".equals(strategy)
                    ? (b == null ? 0 : 1.0 / (RetrievalRules.RRF_K + b.rank()))
                      + (d == null ? 0 : 1.0 / (RetrievalRules.RRF_K + d.rank()))
                    : null;
            result.add(new Candidate(id, b == null ? null : b.rank(), d == null ? null : d.rank(),
                    b == null ? null : b.score(), d == null ? null : d.score(), rrf));
        }
        result.sort(Comparator.<Candidate>comparingDouble(hit -> hit.score(strategy)).reversed()
                .thenComparing(hit -> hit.chunkId().toString()));
        return List.copyOf(result);
    }

    private static Map<UUID, RankedChunk> byId(List<RankedChunk> hits) {
        Map<UUID, RankedChunk> result = new HashMap<>();
        for (RankedChunk hit : hits) {
            result.merge(hit.chunkId(), hit, (a, b) -> a.rank() <= b.rank() ? a : b);
        }
        return result;
    }
}
