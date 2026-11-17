// 负责人 D。每个事实段使用本轮已存在的 S 编号
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record ModelBlock(String text, List<String> sourceIds) {
    public ModelBlock {
        sourceIds = sourceIds == null ? null : List.copyOf(sourceIds);
    }
}
