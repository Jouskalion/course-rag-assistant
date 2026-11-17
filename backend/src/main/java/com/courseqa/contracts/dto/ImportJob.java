// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record ImportJob(
        @JsonProperty("id") @NotNull UUID id,
        @JsonProperty("document_id") @NotNull UUID documentId,
        @JsonProperty("status") @NotNull @Pattern(regexp="queued|running|completed|failed|interrupted|cancelled") String status,
        @JsonProperty("stage") @NotNull @Pattern(regexp="queued|parsing|indexing|publishing|finished") String stage,
        @JsonProperty("pages_processed") @NotNull Integer pagesProcessed,
        @JsonProperty("pages_total") Integer pagesTotal,
        @JsonProperty("chunks_embedded") @NotNull Integer chunksEmbedded,
        @JsonProperty("chunks_total") Integer chunksTotal,
        @JsonProperty("error") @Valid ApiError error,
        @JsonProperty("created_at") @NotNull Instant createdAt,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("finished_at") Instant finishedAt
) {
}
