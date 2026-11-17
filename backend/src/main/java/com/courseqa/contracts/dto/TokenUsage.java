// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record TokenUsage(
        @JsonProperty("rewrite_input_tokens") @Min(0) Integer rewriteInputTokens,
        @JsonProperty("rewrite_output_tokens") @Min(0) Integer rewriteOutputTokens,
        @JsonProperty("query_embedding_input_tokens") @Min(0) Integer queryEmbeddingInputTokens,
        @JsonProperty("generation_input_tokens") @Min(0) Integer generationInputTokens,
        @JsonProperty("generation_output_tokens") @Min(0) Integer generationOutputTokens,
        @JsonProperty("total_tokens") @Min(0) Integer totalTokens
) {
}
