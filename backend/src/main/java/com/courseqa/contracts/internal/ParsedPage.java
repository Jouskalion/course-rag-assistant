// 负责人 B。页序从 1 开始；text 是位置基准
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record ParsedPage(UUID documentId, int pageNo, String text, String extractionStatus) {
}
