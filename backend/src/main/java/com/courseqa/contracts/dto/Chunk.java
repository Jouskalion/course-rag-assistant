// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Chunk(
        @JsonProperty("id") @NotNull UUID id,
        @JsonProperty("document_id") @NotNull UUID documentId,
        @JsonProperty("knowledge_base_id") @NotNull UUID knowledgeBaseId,
        @JsonProperty("ordinal") @NotNull Integer ordinal,
        @JsonProperty("page_no") @NotNull @Min(1) Integer pageNo,
        @JsonProperty("char_start") @NotNull @Min(0) Integer charStart,
        @JsonProperty("char_end") @NotNull @Min(1) Integer charEnd,
        @JsonProperty("text") @NotNull @CodePointLength(min=0, max=1000) String text
) {
}
