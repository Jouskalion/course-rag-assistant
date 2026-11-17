// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record KnowledgeBaseUpdate(
        @JsonProperty("name") @NotNull @CodePointLength(min=1, max=80) String name,
        @JsonProperty("description") @NotNull @CodePointLength(min=0, max=500) String description
) {
}
