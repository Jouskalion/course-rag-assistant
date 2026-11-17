// 生成文件；字段修改请先改 contracts/openapi.json。负责人 C。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record SearchResult(
        @JsonProperty("query") @NotNull String query,
        @JsonProperty("strategy") @NotNull @Pattern(regexp="bm25|dense|hybrid") String strategy,
        @JsonProperty("document_ids") @NotNull @Valid List<UUID> documentIds,
        @JsonProperty("top_k") @NotNull Integer topK,
        @JsonProperty("hits") @NotNull @Valid List<SearchHit> hits,
        @JsonProperty("query_embedding_ms") Double queryEmbeddingMs,
        @JsonProperty("retrieval_ms") @NotNull Double retrievalMs,
        @JsonProperty("query_embedding_input_tokens") Integer queryEmbeddingInputTokens,
        @JsonProperty("actual_embedding_model") String actualEmbeddingModel
) {
    public SearchResult {
        documentIds = documentIds == null ? null : List.copyOf(documentIds);
        hits = hits == null ? null : List.copyOf(hits);
    }
}
