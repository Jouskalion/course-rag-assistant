// 负责人 D。仅 answered insufficient_evidence needs_clarification
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record ModelAnswer(String answerStatus, List<ModelBlock> answerBlocks, String message) {
    public ModelAnswer {
        answerBlocks = answerBlocks == null ? null : List.copyOf(answerBlocks);
    }
}
