package com.courseqa.retrieval;

import com.courseqa.common.CourseLocks;
import com.courseqa.common.FeatureNotImplementedException;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.port.CatalogReadPort;
import com.courseqa.contracts.port.ScopePort;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CatalogSnapshotRegistry {
    private final CourseLocks courseLocks;
    private final ObjectProvider<ScopePort> scopes;
    private final ObjectProvider<CatalogReadPort> catalogs;
    private final Object lifecycle = new Object();
    private final Map<UUID, CatalogSnapshot> current = new HashMap<>();
    private boolean closed;

    public CatalogSnapshotRegistry(CourseLocks courseLocks, ObjectProvider<ScopePort> scopes,
            ObjectProvider<CatalogReadPort> catalogs) {
        this.courseLocks = courseLocks;
        this.scopes = scopes;
        this.catalogs = catalogs;
    }

    CatalogSnapshotLease acquire(UUID courseId, List<UUID> documentIds, Deadline deadline) {
        RetrievalRules.checkDeadline(deadline);
        return courseLocks.withLock(courseId, () -> {
            RetrievalRules.checkDeadline(deadline);
            ScopePort scope = scopes.getIfAvailable();
            if (scope == null) {
                throw new FeatureNotImplementedException("B", "ScopePort 范围校验接入");
            }
            scope.validateFixed(courseId, documentIds);
            RetrievalRules.checkDeadline(deadline);
            synchronized (lifecycle) {
                ensureOpen();
                CatalogSnapshot cached = current.get(courseId);
                if (cached != null) {
                    return cached.acquire();
                }
            }
            CatalogReadPort catalog = catalogs.getIfAvailable();
            if (catalog == null) {
                throw new FeatureNotImplementedException("B", "CatalogReadPort 资料读取接入");
            }
            // 同一课程锁内读取完整数据并同时构建两路；供应商调用在离开此处后执行。
            CatalogSnapshot created = new CatalogSnapshot(courseId, catalog.load(courseId), deadline);
            try {
                RetrievalRules.checkDeadline(deadline);
                synchronized (lifecycle) {
                    ensureOpen();
                    current.put(courseId, created);
                    return created.acquire();
                }
            } catch (RuntimeException exception) {
                created.retire();
                throw exception;
            }
        });
    }

    void invalidate(UUID courseId) {
        if (courseId == null) {
            throw RetrievalRules.invalid("使索引失效时必须提供课程编号");
        }
        // B 已持有课程锁时为同一把可重入锁，避免另建互斥机制。
        courseLocks.withLock(courseId, () -> {
            synchronized (lifecycle) {
                CatalogSnapshot previous = current.remove(courseId);
                if (previous != null) {
                    previous.retire();
                }
            }
            return null;
        });
    }

    private void ensureOpen() {
        if (closed) {
            throw new ServiceException("TASK_INTERRUPTED", 503, "检索服务正在关闭", true);
        }
    }

    @PreDestroy
    public void close() {
        synchronized (lifecycle) {
            if (!closed) {
                closed = true;
                current.values().forEach(CatalogSnapshot::retire);
                current.clear();
            }
        }
    }
}
