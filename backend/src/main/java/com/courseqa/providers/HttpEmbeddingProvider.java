// 负责人 C。实现此类并通过 Spring Bean 装配接入。
package com.courseqa.providers;
import com.courseqa.contracts.port.*;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public class HttpEmbeddingProvider implements EmbeddingProvider {
    @Override public EmbeddingBatchResult embedDocuments(List<ChunkDraft> chunks, Deadline deadline) { throw new FeatureNotImplementedException("C","embedDocuments"); }
    @Override public QueryVector embedQuery(String query, Deadline deadline) { throw new FeatureNotImplementedException("C","embedQuery"); }
}
