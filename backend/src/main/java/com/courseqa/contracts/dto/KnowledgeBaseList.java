// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record KnowledgeBaseList(
        @JsonProperty("items") @NotNull @Valid List<KnowledgeBase> items,
        @JsonProperty("page") @NotNull @Min(1) Integer page,
        @JsonProperty("page_size") @NotNull @Min(1) @Max(100) Integer pageSize,
        @JsonProperty("total") @NotNull @Min(0) Integer total
) {
    public KnowledgeBaseList {
        items = items == null ? null : List.copyOf(items);
    }
}
