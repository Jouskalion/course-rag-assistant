package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.ChunkDraft;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.internal.EmbeddedBatch;
import com.courseqa.contracts.internal.EmbeddedChunk;
import com.courseqa.contracts.internal.EmbeddingBatchResult;
import com.courseqa.contracts.internal.EmbeddingBuildSummary;
import com.courseqa.contracts.port.EmbeddingProvider;
import com.courseqa.contracts.port.IndexPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class DefaultIndexService implements IndexPort {
    private final EmbeddingProvider embeddings;
    private final CatalogSnapshotRegistry snapshots;

    public DefaultIndexService(EmbeddingProvider embeddings, CatalogSnapshotRegistry snapshots) {
        this.embeddings = embeddings;
        this.snapshots = snapshots;
    }

    @Override
    public EmbeddingBuildSummary embed(List<ChunkDraft> chunks, Consumer<EmbeddedBatch> onBatch,
            BooleanSupplier cancelled) {
        if (chunks == null || onBatch == null || cancelled == null) {
            throw RetrievalRules.invalid("向量构建必须提供片段、保存回调和取消标记");
        }
        checkCancelled(cancelled);
        List<ChunkDraft> input = new ArrayList<>(chunks);
        var ids = new HashSet<UUID>();
        for (ChunkDraft chunk : input) {
            checkCancelled(cancelled);
            RetrievalRules.validateDraft(chunk);
            if (!ids.add(chunk.chunkId())) {
                throw RetrievalRules.invalid("向量构建不能包含重复片段编号");
            }
        }
        int completed = 0;
        while (completed < input.size()) {
            checkCancelled(cancelled);
            List<ChunkDraft> batch = List.copyOf(input.subList(completed,
                    Math.min(completed + RetrievalRules.BATCH_SIZE, input.size())));
            // 每批允许两次各最多 30 秒的请求及一次 1 秒等待，不限制整份 PDF 的总时长。
            Deadline deadline = new Deadline(System.nanoTime() + 2 * RetrievalRules.REQUEST_NANOS + 1_000_000_000L);
            EmbeddingBatchResult result;
            try {
                result = embeddings.embedDocuments(batch, deadline);
            } catch (RuntimeException failure) {
                // 在途请求失败时也优先识别已经发生的取消，避免把删除取消记为模型失败。
                checkCancelled(cancelled);
                throw failure;
            }
            checkCancelled(cancelled);
            RetrievalRules.checkDeadline(deadline);
            List<EmbeddedChunk> checked = validateResult(batch, result);
            checkCancelled(cancelled);
            int next = completed + batch.size();
            // B 在回调中短事务保存。失败原样上抛，不继续后续批次、不发布 ready。
            onBatch.accept(new EmbeddedBatch(checked, next, input.size()));
            completed = next;
            checkCancelled(cancelled);
        }
        return new EmbeddingBuildSummary(completed, input.size());
    }

    private static List<EmbeddedChunk> validateResult(List<ChunkDraft> batch, EmbeddingBatchResult result) {
        if (result == null || result.vectors() == null || result.vectors().size() != batch.size()) {
            throw RetrievalRules.invalidVector();
        }
        List<EmbeddedChunk> checked = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            EmbeddedChunk vector = result.vectors().get(i);
            if (vector == null || !batch.get(i).chunkId().equals(vector.chunkId())) {
                throw new ServiceException("EMBEDDING_FAILED", 502, "向量片段编号或返回顺序与输入不一致", true);
            }
            RetrievalRules.checkModel(vector.model(), vector.dimensions());
            checked.add(new EmbeddedChunk(vector.chunkId(), vector.model(), vector.dimensions(),
                    RetrievalRules.normalized(vector.vector())));
        }
        return List.copyOf(checked);
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("资料导入已取消");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new ServiceException("TASK_INTERRUPTED", 503, "资料向量构建已中断", true);
        }
    }

    @Override
    public void invalidate(UUID knowledgeBaseId) {
        snapshots.invalidate(knowledgeBaseId);
    }
}
