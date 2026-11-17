// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Health(
        @JsonProperty("status") @NotNull @Pattern(regexp="ok") String status,
        @JsonProperty("version") @NotNull String version,
        @JsonProperty("application_stage") @NotNull @Pattern(regexp="skeleton|application") String applicationStage,
        @JsonProperty("database_ready") @NotNull Boolean databaseReady,
        @JsonProperty("model_configured") @NotNull Boolean modelConfigured
) {
}
