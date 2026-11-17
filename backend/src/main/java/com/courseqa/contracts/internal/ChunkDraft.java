// 负责人 B。码点区间左闭右开；不跨页
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record ChunkDraft(UUID chunkId, UUID documentId, int ordinal, int pageNo, int charStart, int charEnd, String text, String searchText) {
}
