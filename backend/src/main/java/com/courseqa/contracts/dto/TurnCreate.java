// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record TurnCreate(
        @JsonProperty("client_request_id") @NotNull UUID clientRequestId,
        @JsonProperty("question") @NotNull @CodePointLength(min=1, max=2000) String question,
        @JsonProperty("strategy") @Pattern(regexp="bm25|dense|hybrid") String strategy,
        @JsonProperty("top_k") @Min(1) @Max(10) Integer topK
) {
}
