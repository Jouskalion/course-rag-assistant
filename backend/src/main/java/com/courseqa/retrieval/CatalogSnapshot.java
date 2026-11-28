package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.CatalogChunk;
import com.courseqa.contracts.internal.CatalogData;
import com.courseqa.contracts.internal.Deadline;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 同一版本的片段、关键词索引和向量数组；退役不打断活跃读者。 */
final class CatalogSnapshot {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogSnapshot.class);
    private final Map<UUID, CatalogChunk> chunks;
    private final LuceneBm25Index bm25;
    private final DenseRetriever dense;
    private int readers;
    private boolean retired;
    private boolean closed;

    CatalogSnapshot(UUID courseId, CatalogData data, Deadline deadline) {
        if (data == null || !courseId.equals(data.knowledgeBaseId()) || data.chunks() == null
                || data.chunks().size() > RetrievalRules.MAX_CHUNKS) {
            throw invalidCatalog();
        }
        Map<UUID, CatalogChunk> checked = new HashMap<>();
        for (CatalogChunk chunk : data.chunks()) {
            RetrievalRules.checkDeadline(deadline);
            if (chunk == null || !courseId.equals(chunk.knowledgeBaseId()) || chunk.documentId() == null
                    || chunk.chunkId() == null || chunk.fileName() == null || chunk.fileName().isBlank()
                    || chunk.pageNo() < 1 || chunk.charStart() < 0 || chunk.charEnd() <= chunk.charStart()
                    || chunk.text() == null || chunk.searchText() == null || chunk.searchText().isBlank()) {
                throw invalidCatalog();
            }
            int length = chunk.text().codePointCount(0, chunk.text().length());
            if (length < 1 || length > 1000 || (long) chunk.charEnd() - chunk.charStart() != length
                    || checked.putIfAbsent(chunk.chunkId(), chunk) != null) {
                throw invalidCatalog();
            }
        }
        chunks = Map.copyOf(checked);
        List<CatalogChunk> ordered = checked.values().stream()
                .sorted(java.util.Comparator.comparing(chunk -> chunk.chunkId().toString())).toList();
        dense = new DenseRetriever(ordered, deadline);
        bm25 = new LuceneBm25Index(ordered, deadline);
    }

    Map<UUID, CatalogChunk> chunks() { return chunks; }
    LuceneBm25Index bm25() { return bm25; }
    DenseRetriever dense() { return dense; }

    synchronized CatalogSnapshotLease acquire() {
        if (retired || closed) {
            throw new IllegalStateException("不能取得已退役快照的新租约");
        }
        readers++;
        return new CatalogSnapshotLease(this);
    }

    synchronized void release() {
        if (readers <= 0) {
            throw new IllegalStateException("快照租约计数不一致");
        }
        readers--;
        closeIfUnused();
    }

    synchronized void retire() {
        retired = true;
        closeIfUnused();
    }

    private void closeIfUnused() {
        if (retired && readers == 0 && !closed) {
            closed = true;
            try {
                bm25.close();
            } catch (IOException | RuntimeException exception) {
                // 缓存已不可取得；资源清理失败不能撤销 B 已提交的发布或删除。
                LOG.error("退役检索快照资源关闭失败", exception);
            }
        }
    }

    private static ServiceException invalidCatalog() {
        return new ServiceException("INTERNAL_ERROR", 500, "课程可检索片段快照不符合约定", false);
    }
}
