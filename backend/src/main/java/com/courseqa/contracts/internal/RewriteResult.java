// 负责人 D。需澄清时问题为空；否则独立问题非空
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record RewriteResult(boolean needsClarification, String standaloneQuestion, String message, ProviderUsage usage, String actualModel) {
}
