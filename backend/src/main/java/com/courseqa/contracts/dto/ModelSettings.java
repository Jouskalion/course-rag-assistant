// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record ModelSettings(
        @JsonProperty("generation_model") @NotNull String generationModel,
        @JsonProperty("actual_generation_model") String actualGenerationModel,
        @JsonProperty("embedding_model") @NotNull String embeddingModel,
        @JsonProperty("actual_embedding_model") String actualEmbeddingModel,
        @JsonProperty("embedding_dimensions") @NotNull Integer embeddingDimensions,
        @JsonProperty("temperature") @NotNull Double temperature,
        @JsonProperty("max_output_tokens") @NotNull Integer maxOutputTokens,
        @JsonProperty("enable_thinking") @NotNull Boolean enableThinking
) {
}
