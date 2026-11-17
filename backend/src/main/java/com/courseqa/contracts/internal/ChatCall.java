// 负责人 D。原始模型内容；由问答层解析、校验，保留原文供一次结构修复
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record ChatCall(String content, ProviderUsage usage, String actualModel) {
}
