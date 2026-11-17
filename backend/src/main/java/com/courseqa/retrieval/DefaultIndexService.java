// 负责人 C。实现此类并通过 Spring Bean 装配接入。
package com.courseqa.retrieval;
import com.courseqa.contracts.port.*;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public class DefaultIndexService implements IndexPort {
    @Override public EmbeddingBuildSummary embed(List<ChunkDraft> chunks, Consumer<EmbeddedBatch> onBatch, BooleanSupplier cancelled) { throw new FeatureNotImplementedException("C","embed"); }
    @Override public void invalidate(UUID knowledgeBaseId) { throw new FeatureNotImplementedException("C","invalidate"); }
}
