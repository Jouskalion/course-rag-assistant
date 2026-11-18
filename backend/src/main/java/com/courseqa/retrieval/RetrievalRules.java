package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.SearchRequest;
import com.courseqa.contracts.internal.ChunkDraft;
import com.courseqa.contracts.internal.Deadline;
import java.util.HashSet;
import java.util.Set;

/** C 模块的固定参数和内部调用校验；HTTP 与问答使用相同规则。 */
final class RetrievalRules {
    static final String MODEL = "text-embedding-v4";
    static final int DIMENSIONS = 1024;
    static final int BATCH_SIZE = 10;
    static final int CANDIDATES = 20;
    static final int DEFAULT_TOP_K = 6;
    static final int MAX_TOP_K = 10;
    static final int MAX_CHUNKS = 5000;
    static final int RRF_K = 60;
    static final long REQUEST_NANOS = 30_000_000_000L;
    static final long SEARCH_NANOS = 60_000_000_000L;

    private RetrievalRules() {}

    static SearchRequest validate(SearchRequest request) {
        if (request == null || request.knowledgeBaseId() == null) {
            throw invalid("必须提供课程编号");
        }
        var ids = request.documentIds();
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("EMPTY_SCOPE", 422, "请选择至少一份资料", false);
        }
        if (ids.size() > 100 || ids.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw invalid("资料编号必须非空、不重复，且最多 100 个");
        }
        if (request.query() == null || request.query().isBlank()
                || request.query().codePointCount(0, request.query().length()) > 2000) {
            throw invalid("检索问题必须为 1 至 2000 个 Unicode 码点的非空文字");
        }
        if (request.strategy() == null
                || !Set.of("bm25", "dense", "hybrid").contains(request.strategy())) {
            throw invalid("检索策略必须为 bm25、dense 或 hybrid");
        }
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        if (topK < 1 || topK > MAX_TOP_K) {
            throw invalid("top_k 必须为 1 至 10 的整数");
        }
        return new SearchRequest(request.knowledgeBaseId(), ids, request.query(), request.strategy(), topK);
    }

    static void validateDraft(ChunkDraft chunk) {
        if (chunk == null || chunk.chunkId() == null || chunk.documentId() == null
                || chunk.ordinal() < 0 || chunk.pageNo() < 1 || chunk.charStart() < 0
                || chunk.charEnd() <= chunk.charStart() || chunk.text() == null
                || chunk.searchText() == null || chunk.searchText().isBlank()) {
            throw invalid("待向量化片段的编号、页码、位置或文字不完整");
        }
        int length = chunk.text().codePointCount(0, chunk.text().length());
        if (length < 1 || length > 1000 || (long) chunk.charEnd() - chunk.charStart() != length) {
            throw invalid("片段原文必须为 1 至 1000 码点，且与页内位置一致");
        }
    }

    static void checkDeadline(Deadline deadline) {
        if (Thread.currentThread().isInterrupted()) {
            throw new ServiceException("TASK_INTERRUPTED", 503, "检索执行已中断", true);
        }
        if (deadline == null) {
            throw invalid("调用检索时必须提供执行期限");
        }
        if (deadline.deadlineNanos() - System.nanoTime() <= 0) {
            throw timeout();
        }
    }

    static ServiceException timeout() {
        return new ServiceException("MODEL_TIMEOUT", 504, "本轮执行已超过期限", true);
    }

    static ServiceException invalid(String message) {
        return new ServiceException("INVALID_REQUEST", 422, message, false);
    }

    static ServiceException invalidVector() {
        return new ServiceException("EMBEDDING_FAILED", 502, "向量必须具有正确维度、有限分量和非零长度", true);
    }

    static void checkModel(String model, int dimensions) {
        if (!MODEL.equals(model) || dimensions != DIMENSIONS) {
            throw new ServiceException("EMBEDDING_MODEL_MISMATCH", 409,
                    "课程片段与问题必须使用 text-embedding-v4 的 1024 维向量；更换模型需要整库重建", false);
        }
    }

    static float[] normalized(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) {
            throw invalidVector();
        }
        double squaredNorm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw invalidVector();
            }
            squaredNorm += (double) value * value;
        }
        if (!(squaredNorm > 0) || !Double.isFinite(squaredNorm)) {
            throw invalidVector();
        }
        double norm = Math.sqrt(squaredNorm);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
}
