// 负责人 B C。数据库的完整可检索课程快照
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record CatalogData(UUID knowledgeBaseId, List<CatalogChunk> chunks) {
    public CatalogData {
        chunks = chunks == null ? null : List.copyOf(chunks);
    }
}
