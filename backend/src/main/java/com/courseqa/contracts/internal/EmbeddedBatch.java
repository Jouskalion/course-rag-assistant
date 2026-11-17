// 负责人 C。B 在回调中短事务保存进度
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record EmbeddedBatch(List<EmbeddedChunk> chunks, int completed, int total) {
    public EmbeddedBatch {
        chunks = chunks == null ? null : List.copyOf(chunks);
    }
}
