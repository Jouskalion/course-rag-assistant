// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record FeedbackWrite(
        @JsonProperty("rating") @NotNull @Pattern(regexp="up|down") String rating,
        @JsonProperty("reason") @Pattern(regexp="helpful|incorrect|unsupported|missing_information|other") String reason,
        @JsonProperty("comment") @NotNull @CodePointLength(min=0, max=1000) String comment
) {
}
