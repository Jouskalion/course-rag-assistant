// 实现负责人 C；调用方 B。
package com.courseqa.contracts.port;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public interface IndexPort {
    /** 分批向量化；B 保存每批；取消后抛出明确异常；不修改资料状态 */
    EmbeddingBuildSummary embed(List<ChunkDraft> chunks, Consumer<EmbeddedBatch> onBatch, BooleanSupplier cancelled);
    /** 须在课程锁内调用；仅标记缓存失效，保留活跃读取租约 */
    void invalidate(UUID knowledgeBaseId);
}
