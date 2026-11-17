// 负责人 B C。仅加载 ready 且 enabled 资料 不可变副本
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record CatalogChunk(UUID knowledgeBaseId, UUID documentId, UUID chunkId, String fileName, int pageNo, int charStart, int charEnd, String text, String searchText, String model, int dimensions, float[] vector) {
    public CatalogChunk {
        vector = vector == null ? null : vector.clone();
    }
    @Override public float[] vector(){return vector==null?null:vector.clone();}
}
