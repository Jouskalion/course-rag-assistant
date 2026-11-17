// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record DocumentPage(
        @JsonProperty("document_id") @NotNull UUID documentId,
        @JsonProperty("page_no") @NotNull @Min(1) Integer pageNo,
        @JsonProperty("text") @NotNull String text,
        @JsonProperty("char_count") @NotNull Integer charCount,
        @JsonProperty("extraction_status") @NotNull @Pattern(regexp="text|low_text|empty") String extractionStatus
) {
}
