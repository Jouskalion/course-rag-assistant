// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Timings(
        @JsonProperty("queue_ms") Double queueMs,
        @JsonProperty("rewrite_ms") Double rewriteMs,
        @JsonProperty("query_embedding_ms") Double queryEmbeddingMs,
        @JsonProperty("retrieval_ms") Double retrievalMs,
        @JsonProperty("generation_ms") Double generationMs,
        @JsonProperty("validation_ms") Double validationMs,
        @JsonProperty("total_ms") Double totalMs
) {
}
