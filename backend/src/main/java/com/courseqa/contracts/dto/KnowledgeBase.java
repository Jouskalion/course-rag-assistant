// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record KnowledgeBase(
        @JsonProperty("id") @NotNull UUID id,
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("description") @NotNull String description,
        @JsonProperty("embedding_model") @NotNull String embeddingModel,
        @JsonProperty("embedding_dimensions") @NotNull Integer embeddingDimensions,
        @JsonProperty("document_count") @NotNull Integer documentCount,
        @JsonProperty("ready_document_count") @NotNull Integer readyDocumentCount,
        @JsonProperty("ready_chunk_count") @NotNull Integer readyChunkCount,
        @JsonProperty("reserved_chunk_count") @NotNull Integer reservedChunkCount,
        @JsonProperty("created_at") @NotNull Instant createdAt,
        @JsonProperty("updated_at") @NotNull Instant updatedAt
) {
}
