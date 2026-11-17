// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Citation(
        @JsonProperty("citation_id") @NotNull @Min(1) Integer citationId,
        @JsonProperty("document_id") @NotNull UUID documentId,
        @JsonProperty("chunk_id") UUID chunkId,
        @JsonProperty("file_name") @NotNull String fileName,
        @JsonProperty("page_no") @NotNull @Min(1) Integer pageNo,
        @JsonProperty("excerpt") @NotNull @CodePointLength(min=0, max=1000) String excerpt,
        @JsonProperty("source_available") @NotNull Boolean sourceAvailable
) {
}
