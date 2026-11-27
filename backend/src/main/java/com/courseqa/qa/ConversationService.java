package com.courseqa.qa;

import com.courseqa.common.CourseLocks;
import com.courseqa.common.FeatureNotImplementedException;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.Conversation;
import com.courseqa.contracts.dto.ConversationCreate;
import com.courseqa.contracts.dto.ConversationList;
import com.courseqa.contracts.port.ScopePort;
import com.courseqa.db.WriteTransactions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private final ConversationRepository conversations;
    private final ObjectProvider<ScopePort> scopes;
    private final CourseLocks locks;
    private final WriteTransactions writes;

    public ConversationService(ConversationRepository conversations, ObjectProvider<ScopePort> scopes,
            CourseLocks locks, WriteTransactions writes) {
        this.conversations = conversations;
        this.scopes = scopes;
        this.locks = locks;
        this.writes = writes;
    }

    ScopePort scope() {
        var scope = scopes.getIfAvailable();
        if (scope == null) throw new FeatureNotImplementedException("B", "ScopePort 资料范围与原文件查询");
        return scope;
    }

    Map<UUID, Boolean> availability(List<UUID> documents) {
        return documents.isEmpty() ? Map.of() : scope().sourceAvailability(documents);
    }

    Conversation create(ConversationCreate input) {
        return locks.withLock(input.knowledgeBaseId(), () -> writes.execute(() -> {
            var ids = scope().resolve(input.knowledgeBaseId(), input.scope(), input.documentIds());
            if (ids.isEmpty()) throw new ServiceException("EMPTY_SCOPE", 422, "本课程没有可用于该会话的资料", false);
            return conversations.insert(input, ids);
        }));
    }

    ConversationList list(UUID course, QaRequests.Page page) {
        return locks.withLock(course, () -> {
            var rows = conversations.list(course, page);
            return new ConversationList(rows.items().stream().map(this::annotateScope).toList(),
                    rows.page(), rows.pageSize(), rows.total());
        });
    }

    Conversation get(UUID id) {
        var course = conversations.get(id).knowledgeBaseId();
        return locks.withLock(course, () -> annotateScope(conversations.get(id)));
    }

    Conversation rename(UUID id, String title) {
        var course = conversations.get(id).knowledgeBaseId();
        return locks.withLock(course, () -> writes.execute(() -> {
            conversations.rename(id, title);
            return annotateScope(conversations.get(id));
        }));
    }

    void delete(UUID id) {
        var course = conversations.get(id).knowledgeBaseId();
        locks.withLock(course, () -> writes.execute(() -> {
            if (conversations.get(id).activeTurnId() != null) throw QaRequests.busy();
            conversations.delete(id);
            return null;
        }));
    }

    private Conversation annotateScope(Conversation c) {
        var scope = scope();
        try {
            scope.validateFixed(c.knowledgeBaseId(), c.documentIds());
            return c;
        } catch (ServiceException ex) {
            if (!"SOURCE_UNAVAILABLE".equals(ex.asError().code())) throw ex;
        }
        // A disabled file may still open. This list describes search eligibility, not PDF availability.
        var unavailable = new ArrayList<UUID>();
        for (var id : c.documentIds()) {
            try {
                scope.validateFixed(c.knowledgeBaseId(), List.of(id));
            } catch (ServiceException ex) {
                if (!"SOURCE_UNAVAILABLE".equals(ex.asError().code())) throw ex;
                unavailable.add(id);
            }
        }
        return new Conversation(c.id(), c.knowledgeBaseId(), c.title(), c.scope(), c.documentIds(), unavailable,
                c.activeTurnId(), c.createdAt(), c.updatedAt());
    }
}
