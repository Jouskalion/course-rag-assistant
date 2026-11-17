// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Conversation(
        @JsonProperty("id") @NotNull UUID id,
        @JsonProperty("knowledge_base_id") @NotNull UUID knowledgeBaseId,
        @JsonProperty("title") @NotNull String title,
        @JsonProperty("scope") @NotNull @Pattern(regexp="all|selected") String scope,
        @JsonProperty("document_ids") @NotNull @Size(min=1, max=2147483647) @Valid List<UUID> documentIds,
        @JsonProperty("unavailable_document_ids") @NotNull @Valid List<UUID> unavailableDocumentIds,
        @JsonProperty("active_turn_id") UUID activeTurnId,
        @JsonProperty("created_at") @NotNull Instant createdAt,
        @JsonProperty("updated_at") @NotNull Instant updatedAt
) {
    public Conversation {
        documentIds = documentIds == null ? null : List.copyOf(documentIds);
        unavailableDocumentIds = unavailableDocumentIds == null ? null : List.copyOf(unavailableDocumentIds);
    }
}
