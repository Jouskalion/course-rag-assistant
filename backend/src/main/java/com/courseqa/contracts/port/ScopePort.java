// 实现负责人 B；调用方 D C。
package com.courseqa.contracts.port;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public interface ScopePort {
    /** 创建会话时 all 展开当前可用资料；selected 校验后返回固定集合 */
    List<UUID> resolve(UUID knowledgeBaseId, String scope, List<UUID> documentIds);
    /** 任一失效或跨课程即报错，不能悄悄删减集合 */
    void validateFixed(UUID knowledgeBaseId, List<UUID> documentIds);
    /** 历史引用的原文件可用性；缺失或删除为 false，停用但文件仍在为 true。 */
    Map<UUID, Boolean> sourceAvailability(List<UUID> documentIds);
}
