// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record ApiError(
        @JsonProperty("code") @NotNull @Pattern(regexp="INVALID_REQUEST|NOT_FOUND|METHOD_NOT_ALLOWED|UNSUPPORTED_MEDIA_TYPE|KNOWLEDGE_BASE_NOT_EMPTY|EMPTY_SCOPE|SCOPE_MISMATCH|SOURCE_UNAVAILABLE|FILE_TOO_LARGE|INVALID_PDF|DOCUMENT_LIMIT_EXCEEDED|DOCUMENT_NOT_READY|IDEMPOTENCY_CONFLICT|CONVERSATION_BUSY|IMPORT_NOT_RETRYABLE|TURN_NOT_RETRYABLE|MODEL_NOT_CONFIGURED|EMBEDDING_MODEL_MISMATCH|PDF_TOO_MANY_PAGES|PDF_ENCRYPTED|PDF_NO_TEXT|PDF_PARSE_FAILED|CHUNK_LIMIT_EXCEEDED|EMBEDDING_FAILED|MODEL_AUTH_FAILED|MODEL_RATE_LIMITED|MODEL_TIMEOUT|MODEL_UNAVAILABLE|MODEL_OUTPUT_INVALID|CITATION_INVALID|TASK_INTERRUPTED|DELETE_FAILED|FEATURE_NOT_IMPLEMENTED|INTERNAL_ERROR") String code,
        @JsonProperty("message") @NotNull String message,
        @JsonProperty("retryable") @NotNull Boolean retryable,
        @JsonProperty("details") @NotNull @Valid List<FieldError> details
) {
    public ApiError {
        details = details == null ? null : List.copyOf(details);
    }
}
