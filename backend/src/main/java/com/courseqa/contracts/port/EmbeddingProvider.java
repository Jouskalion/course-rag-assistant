// 实现负责人 C；调用方 C。
package com.courseqa.contracts.port;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public interface EmbeddingProvider {
    /** 最多 10 片段；按供应商 index 还原片段编号；仅 429 和 5xx 最多重试一次 */
    EmbeddingBatchResult embedDocuments(List<ChunkDraft> chunks, Deadline deadline);
    /** 沿用本轮期限；1024 维已归一化向量 */
    QueryVector embedQuery(String query, Deadline deadline);
}
