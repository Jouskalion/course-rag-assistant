// 负责人 C。问题向量与片段采用相同模型
package com.courseqa.contracts.internal;
import java.util.UUID;
import java.util.List;
public record QueryVector(String model, int dimensions, float[] vector, ProviderUsage usage, String actualModel) {
    public QueryVector {
        vector = vector == null ? null : vector.clone();
    }
    @Override public float[] vector(){return vector==null?null:vector.clone();}
}
