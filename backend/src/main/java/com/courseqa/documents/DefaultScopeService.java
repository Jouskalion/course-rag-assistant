// 负责人 B。实现此类并通过 Spring Bean 装配接入。
package com.courseqa.documents;
import com.courseqa.contracts.port.*;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public class DefaultScopeService implements ScopePort {
    @Override public List<UUID> resolve(UUID knowledgeBaseId, String scope, List<UUID> documentIds) { throw new FeatureNotImplementedException("B","resolve"); }
    @Override public void validateFixed(UUID knowledgeBaseId, List<UUID> documentIds) { throw new FeatureNotImplementedException("B","validateFixed"); }
    @Override public Map<UUID, Boolean> sourceAvailability(List<UUID> documentIds) { throw new FeatureNotImplementedException("B","sourceAvailability"); }
}
