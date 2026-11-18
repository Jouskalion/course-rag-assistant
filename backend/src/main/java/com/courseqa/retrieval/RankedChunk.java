package com.courseqa.retrieval;

import java.util.Comparator;
import java.util.UUID;

/** 仅在检索模块内部流转；rank 从 1 开始，在候选截取前确定稳定顺序。 */
record RankedChunk(UUID chunkId, double score, int rank) {
    static final Comparator<RankedChunk> ORDER = Comparator
            .comparingDouble(RankedChunk::score).reversed()
            .thenComparing(hit -> hit.chunkId().toString());
}
