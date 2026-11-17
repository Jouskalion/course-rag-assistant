// 负责人 C。按输入片段编号还原顺序
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record EmbeddingBatchResult(List<EmbeddedChunk> vectors, ProviderUsage usage, String actualModel) {
    public EmbeddingBatchResult {
        vectors = vectors == null ? null : List.copyOf(vectors);
    }
}
