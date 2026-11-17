// 生成文件；字段修改请先改 contracts/openapi.json。负责人 D。
package com.courseqa.contracts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.courseqa.common.CodePointLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public record Turn(
        @JsonProperty("id") @NotNull UUID id,
        @JsonProperty("conversation_id") @NotNull UUID conversationId,
        @JsonProperty("client_request_id") @NotNull UUID clientRequestId,
        @JsonProperty("retry_of_turn_id") UUID retryOfTurnId,
        @JsonProperty("question") @NotNull String question,
        @JsonProperty("standalone_question") String standaloneQuestion,
        @JsonProperty("strategy") @NotNull @Pattern(regexp="bm25|dense|hybrid") String strategy,
        @JsonProperty("top_k") @NotNull Integer topK,
        @JsonProperty("status") @NotNull @Pattern(regexp="queued|rewriting|retrieving|generating|validating|answered|insufficient_evidence|needs_clarification|failed|interrupted") String status,
        @JsonProperty("answer_blocks") @NotNull @Valid List<AnswerBlock> answerBlocks,
        @JsonProperty("citations") @NotNull @Valid List<Citation> citations,
        @JsonProperty("message") String message,
        @JsonProperty("error") @Valid ApiError error,
        @JsonProperty("retrieval") @Valid SearchResult retrieval,
        @JsonProperty("timings") @NotNull @Valid Timings timings,
        @JsonProperty("token_usage") @NotNull @Valid TokenUsage tokenUsage,
        @JsonProperty("model_settings") @NotNull @Valid ModelSettings modelSettings,
        @JsonProperty("feedback") @Valid Feedback feedback,
        @JsonProperty("created_at") @NotNull Instant createdAt,
        @JsonProperty("updated_at") @NotNull Instant updatedAt,
        @JsonProperty("finished_at") Instant finishedAt
) {
    public Turn {
        answerBlocks = answerBlocks == null ? null : List.copyOf(answerBlocks);
        citations = citations == null ? null : List.copyOf(citations);
    }
}
