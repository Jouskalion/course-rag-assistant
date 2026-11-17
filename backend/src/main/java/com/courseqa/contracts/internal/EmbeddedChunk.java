// 负责人 C。已校验、归一化的向量
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record EmbeddedChunk(UUID chunkId, String model, int dimensions, float[] vector) {
    public EmbeddedChunk {
        vector = vector == null ? null : vector.clone();
    }
    @Override public float[] vector(){return vector==null?null:vector.clone();}
}
