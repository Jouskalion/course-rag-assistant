// 生成文件；字段修改请先改 contracts/openapi.json。负责人 C。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record SearchRequest(
        @JsonProperty("knowledge_base_id") @NotNull UUID knowledgeBaseId,
        @JsonProperty("document_ids") @NotNull @Size(min=1, max=100) @Valid List<UUID> documentIds,
        @JsonProperty("query") @NotNull @CodePointLength(min=1, max=2000) String query,
        @JsonProperty("strategy") @NotNull @Pattern(regexp="bm25|dense|hybrid") String strategy,
        @JsonProperty("top_k") @Min(1) @Max(10) Integer topK
) {
    public SearchRequest {
        documentIds = documentIds == null ? null : List.copyOf(documentIds);
    }
}
