// 负责人 B。
package com.courseqa.documents.service;
import com.courseqa.documents.repository.KnowledgeBaseRepository;
import com.courseqa.documents.entity.KnowledgeBaseEntity;
import com.courseqa.documents.entity.DocumentEntity;
import com.courseqa.common.CourseLocks;
import com.courseqa.common.ServiceException;
import com.courseqa.db.WriteTransactions;
import com.courseqa.contracts.dto.KnowledgeBase;
import com.courseqa.contracts.dto.KnowledgeBaseCreate;
import com.courseqa.contracts.dto.KnowledgeBaseList;
import com.courseqa.contracts.dto.KnowledgeBaseUpdate;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
@Service
public class KnowledgeBaseService extends ServiceImpl<KnowledgeBaseRepository, KnowledgeBaseEntity> {
    private final DocumentQueryService documentService;
    private final WriteTransactions transactions;
    private final CourseLocks locks;

    public KnowledgeBaseService(DocumentQueryService documentService, WriteTransactions transactions, CourseLocks locks) {
        this.documentService = documentService;
        this.transactions = transactions;
        this.locks = locks;
    }

    public KnowledgeBaseList list(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数不合法");
        long total = lambdaQuery().count();
        long offset = ((long) page - 1) * pageSize;
        List<KnowledgeBase> items = lambdaQuery()
                        .orderByDesc(KnowledgeBaseEntity::getCreatedAt)
                        .orderByAsc(KnowledgeBaseEntity::getId)
                        .last("LIMIT " + offset + "," + pageSize)
                        .list()
                        .stream()
                        .map(this::toDto)
                        .toList();
        return new KnowledgeBaseList(items, page, pageSize, (int) total);
    }

    public KnowledgeBase create(KnowledgeBaseCreate body) {
        String name = body.name() == null ? "" : body.name().strip();
        validateName(name);
        Instant now = Instant.now();
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
        entity.setName(name);
        entity.setDescription(body.description() == null ? "" : body.description());
        entity.setEmbeddingModel("text-embedding-v4");
        entity.setEmbeddingDimensions(1024);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        transactions.execute(() -> {
            save(entity);
            return null;
        });
        return toDto(entity);
    }

    public KnowledgeBase get(UUID knowledgeBaseId) {
        KnowledgeBaseEntity entity = getById(key(knowledgeBaseId));
        if (entity == null) throw notFound();
        return toDto(entity);
    }

    public KnowledgeBase update(UUID knowledgeBaseId, KnowledgeBaseUpdate body) {
        String id = key(knowledgeBaseId);
        String name = body.name() == null ? "" : body.name().strip();
        validateName(name);
        Instant updatedAt = Instant.now();
        // 与 delete 共用课程锁，避免读后删导致更新 0 行仍返回 200。
        KnowledgeBaseEntity entity = locks.withLock(knowledgeBaseId, () -> transactions.execute(() -> {
            KnowledgeBaseEntity current = getById(id);
            if (current == null) throw notFound();
            current.setName(name);
            current.setDescription(body.description());
            current.setUpdatedAt(updatedAt);
            if (!updateById(current)) throw notFound();
            return current;
        }));
        return toDto(entity);
    }

    public void delete(UUID knowledgeBaseId) {
        String id = key(knowledgeBaseId);
        locks.withLock(knowledgeBaseId, () -> transactions.execute(() -> {
            if (getById(id) == null) throw notFound();
            long documents = documentService.lambdaQuery()
                    .eq(DocumentEntity::getKnowledgeBaseId, id)
                    .ne(DocumentEntity::getStatus, "deleted")
                    .count();
            if (documents > 0 || getBaseMapper().countConversations(id) > 0 || getBaseMapper().countActiveJobs(id) > 0) {
                throw new ServiceException("KNOWLEDGE_BASE_NOT_EMPTY", 409, "课程仍有资料、会话或活动任务", false);
            }
            // 前置校验忽略墓碑，但 FK RESTRICT 要求先物理删墓碑，子表由级联清理。
            documentService.lambdaUpdate()
                    .eq(DocumentEntity::getKnowledgeBaseId, id)
                    .remove();
            removeById(id);
            return null;
        }));
    }

    private KnowledgeBase toDto(KnowledgeBaseEntity entity) {
        int[] stats = stats(entity.getId());
        return new KnowledgeBase(
                UUID.fromString(entity.getId()),
                entity.getName(),
                entity.getDescription() == null ? "" : entity.getDescription(),
                entity.getEmbeddingModel(),
                entity.getEmbeddingDimensions(),
                stats[0], stats[1], stats[2], stats[3],
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private int[] stats(String knowledgeBaseId) {
        long documentCount = documentService.lambdaQuery()
                .eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .ne(DocumentEntity::getStatus, "deleted")
                .count();
        long readyDocumentCount = documentService.lambdaQuery()
                .eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(DocumentEntity::getStatus, "ready")
                .eq(DocumentEntity::getEnabled, 1)
                .count();
        List<DocumentEntity> readyDocs = documentService.lambdaQuery()
                .eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(DocumentEntity::getStatus, "ready")
                .select(DocumentEntity::getChunkCount)
                .list();
        List<DocumentEntity> allDocs = documentService.lambdaQuery()
                .eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .ne(DocumentEntity::getStatus, "deleted")
                .select(DocumentEntity::getReservedChunkCount)
                .list();
        int readyChunkCount = readyDocs.stream().mapToInt(d -> d.getChunkCount() == null ? 0 : d.getChunkCount()).sum();
        int reservedChunkCount = allDocs.stream().mapToInt(d -> d.getReservedChunkCount() == null ? 0 : d.getReservedChunkCount()).sum();
        return new int[]{(int) documentCount, (int) readyDocumentCount, readyChunkCount, reservedChunkCount};
    }

    private void validateName(String name) {
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > 80) throw invalid("课程名称去掉首尾空白后须为 1 至 80 码点");
    }

    private String key(UUID id) { return id.toString().toLowerCase(Locale.ROOT); }

    private ServiceException notFound() { return new ServiceException("NOT_FOUND", 404, "课程不存在", false); }

    private ServiceException invalid(String message) { return new ServiceException("INVALID_REQUEST", 422, message, false); }
}
