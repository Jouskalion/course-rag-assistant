// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record AnswerBlock(
        @JsonProperty("text") @NotNull @CodePointLength(min=1, max=2147483647) String text,
        @JsonProperty("citation_ids") @NotNull @Size(min=1, max=2147483647) @Valid List<Integer> citationIds
) {
    public AnswerBlock {
        citationIds = citationIds == null ? null : List.copyOf(citationIds);
    }
}
