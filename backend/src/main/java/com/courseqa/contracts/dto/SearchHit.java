// 生成文件；字段修改请先改 contracts/openapi.json。负责人 C。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record SearchHit(
        @JsonProperty("source_id") @NotNull String sourceId,
        @JsonProperty("chunk_id") @NotNull UUID chunkId,
        @JsonProperty("document_id") @NotNull UUID documentId,
        @JsonProperty("file_name") @NotNull String fileName,
        @JsonProperty("page_no") @NotNull @Min(1) Integer pageNo,
        @JsonProperty("char_start") @NotNull Integer charStart,
        @JsonProperty("char_end") @NotNull Integer charEnd,
        @JsonProperty("text") @NotNull @CodePointLength(min=0, max=1000) String text,
        @JsonProperty("bm25_rank") @Min(1) Integer bm25Rank,
        @JsonProperty("dense_rank") @Min(1) Integer denseRank,
        @JsonProperty("bm25_score") Double bm25Score,
        @JsonProperty("dense_score") Double denseScore,
        @JsonProperty("rrf_score") Double rrfScore
) {
}
