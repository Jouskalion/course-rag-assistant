// 负责人 B。
package com.courseqa.documents.service;
import com.courseqa.documents.repository.DocumentRepository;
import com.courseqa.documents.entity.DocumentEntity;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.port.ScopePort;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
public class DefaultScopeService extends ServiceImpl<DocumentRepository, DocumentEntity> implements ScopePort {
    private final Path dataRoot;

    public DefaultScopeService(@Value("${app.home}") String appHome,
                               @Value("${app.data-dir}") String dataDir) {
        this.dataRoot = Path.of(appHome).toAbsolutePath().normalize().resolve(dataDir).normalize();
    }

    @Override
    public List<UUID> resolve(UUID knowledgeBaseId, String scope, List<UUID> documentIds) {
        String knowledgeBase = key(knowledgeBaseId);
        if ("all".equals(scope)) {
            return lambdaQuery()
                            .eq(DocumentEntity::getKnowledgeBaseId, knowledgeBase)
                            .eq(DocumentEntity::getStatus, "ready")
                            .eq(DocumentEntity::getEnabled, 1)
                            .isNull(DocumentEntity::getDeletedAt)
                            .orderByAsc(DocumentEntity::getCreatedAt)
                            .orderByAsc(DocumentEntity::getId)
                            .list()
                            .stream()
                            .map(document -> UUID.fromString(document.getId()))
                            .toList();
        }
        if ("selected".equals(scope)) {
            validateFixed(knowledgeBaseId, documentIds);
            return List.copyOf(new LinkedHashSet<>(documentIds));
        }
        throw invalid("scope 只能是 all 或 selected");
    }

    @Override
    public void validateFixed(UUID knowledgeBaseId, List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new ServiceException("EMPTY_SCOPE", 422, "资料范围不能为空", false);
        }
        String knowledgeBase = key(knowledgeBaseId);
        Set<String> wanted = keys(documentIds);
        List<DocumentEntity> found = lambdaQuery().in(DocumentEntity::getId, wanted).list();
        if (found.size() != wanted.size()) {
            throw new ServiceException("SOURCE_UNAVAILABLE", 409, "范围中存在不存在或已删除的资料", false);
        }
        for (DocumentEntity document : found) {
            if (!knowledgeBase.equals(document.getKnowledgeBaseId())) {
                throw new ServiceException("SCOPE_MISMATCH", 422, "资料不属于当前课程", false);
            }
            if (!"ready".equals(document.getStatus())
                    || document.getEnabled() == null
                    || document.getEnabled() != 1
                    || document.getDeletedAt() != null) {
                throw new ServiceException("SOURCE_UNAVAILABLE", 409, "范围中存在未就绪或已停用的资料", false);
            }
        }
    }

    @Override
    public Map<UUID, Boolean> sourceAvailability(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return Map.of();
        Set<String> wanted = keys(documentIds);
        Map<String, DocumentEntity> found = lambdaQuery().in(DocumentEntity::getId, wanted).list().stream()
                .collect(Collectors.toMap(DocumentEntity::getId, Function.identity()));
        Map<UUID, Boolean> result = new HashMap<>();
        for (String id : wanted) {
            DocumentEntity document = found.get(id);
            boolean available = document != null
                    && !"deleting".equals(document.getStatus())
                    && !"deleted".equals(document.getStatus())
                    && document.getStorageRelpath() != null
                    && fileExists(document.getStorageRelpath());
            result.put(UUID.fromString(id), available);
        }
        return Map.copyOf(result);
    }

    private boolean fileExists(String relativePath) {
        Path file = dataRoot.resolve(relativePath).normalize();
        if (!file.startsWith(dataRoot)) return false;
        return Files.isRegularFile(file);
    }

    private Set<String> keys(List<UUID> ids) {
        return ids.stream().map(this::key).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String key(UUID id) { return id.toString().toLowerCase(Locale.ROOT); }

    private ServiceException invalid(String message) { return new ServiceException("INVALID_REQUEST", 422, message, false); }
}
