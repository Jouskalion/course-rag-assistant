// 生成文件；字段修改请先改 contracts/openapi.json。负责人 B。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Document(
        @JsonProperty("id") @NotNull UUID id,
        @JsonProperty("knowledge_base_id") @NotNull UUID knowledgeBaseId,
        @JsonProperty("file_name") @NotNull String fileName,
        @JsonProperty("file_size_bytes") @NotNull Integer fileSizeBytes,
        @JsonProperty("page_count") Integer pageCount,
        @JsonProperty("chunk_count") @NotNull Integer chunkCount,
        @JsonProperty("status") @NotNull @Pattern(regexp="queued|parsing|indexing|ready|failed|interrupted|deleting|deleted") String status,
        @JsonProperty("enabled") @NotNull Boolean enabled,
        @JsonProperty("warnings") @NotNull @Valid List<DocumentWarning> warnings,
        @JsonProperty("error") @Valid ApiError error,
        @JsonProperty("active_job_id") UUID activeJobId,
        @JsonProperty("created_at") @NotNull Instant createdAt,
        @JsonProperty("updated_at") @NotNull Instant updatedAt,
        @JsonProperty("deleted_at") Instant deletedAt
) {
    public Document {
        warnings = warnings == null ? null : List.copyOf(warnings);
    }
}
