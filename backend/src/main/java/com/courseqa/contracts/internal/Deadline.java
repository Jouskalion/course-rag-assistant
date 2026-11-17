// 负责人 D。整个问答共用的 System.nanoTime 截止时刻
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record Deadline(long deadlineNanos) {
}
