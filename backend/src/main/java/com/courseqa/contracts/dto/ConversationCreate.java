// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record ConversationCreate(
        @JsonProperty("knowledge_base_id") @NotNull UUID knowledgeBaseId,
        @JsonProperty("scope") @NotNull @Pattern(regexp="all|selected") String scope,
        @JsonProperty("document_ids") @Size(min=1, max=100) @Valid List<UUID> documentIds,
        @JsonProperty("title") @CodePointLength(min=1, max=80) String title
) {
    public ConversationCreate {
        documentIds = documentIds == null ? null : List.copyOf(documentIds);
    }
}
