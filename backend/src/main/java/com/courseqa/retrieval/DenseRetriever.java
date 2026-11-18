package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.CatalogChunk;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.internal.QueryVector;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 不可变快照内的精确向量检索，向量数组不离开本类。 */
final class DenseRetriever {
    private static final Logger LOG = LoggerFactory.getLogger(DenseRetriever.class);
    private final List<CatalogChunk> chunks;
    private final float[][] vectors;

    DenseRetriever(List<CatalogChunk> chunks, Deadline deadline) {
        this.chunks = List.copyOf(chunks);
        this.vectors = new float[chunks.size()][];
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalRules.checkDeadline(deadline);
            CatalogChunk chunk = chunks.get(i);
            RetrievalRules.checkModel(chunk.model(), chunk.dimensions());
            vectors[i] = RetrievalRules.normalized(chunk.vector(), () -> invalidStoredVector(chunk));
        }
    }

    private static ServiceException invalidStoredVector(CatalogChunk chunk) {
        LOG.error("课程 {} 资料 {} 片段 {} 的已存向量无效", chunk.knowledgeBaseId(), chunk.documentId(), chunk.chunkId());
        return new ServiceException("INTERNAL_ERROR", 500, "课程已保存的片段向量无效，请检查并修复该资料的向量", false);
    }

    List<RankedChunk> search(QueryVector query, Set<UUID> documentIds, Deadline deadline) {
        RetrievalRules.checkDeadline(deadline);
        if (query == null) {
            throw RetrievalRules.invalidVector();
        }
        RetrievalRules.checkModel(query.model(), query.dimensions());
        float[] queryVector = RetrievalRules.normalized(query.vector());
        List<RankedChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalRules.checkDeadline(deadline);
            CatalogChunk chunk = chunks.get(i);
            if (!documentIds.contains(chunk.documentId())) {
                continue;
            }
            double dot = 0;
            for (int dimension = 0; dimension < queryVector.length; dimension++) {
                dot += (double) queryVector[dimension] * vectors[i][dimension];
            }
            // float32 的归一化舍入可能使点积略超出余弦的数学范围。
            scored.add(new RankedChunk(chunk.chunkId(), Math.max(-1, Math.min(1, dot)), 0));
        }
        scored.sort(RankedChunk.ORDER);
        List<RankedChunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(RetrievalRules.CANDIDATES, scored.size()); i++) {
            RankedChunk hit = scored.get(i);
            result.add(new RankedChunk(hit.chunkId(), hit.score(), i + 1));
        }
        RetrievalRules.checkDeadline(deadline);
        return List.copyOf(result);
    }
}
