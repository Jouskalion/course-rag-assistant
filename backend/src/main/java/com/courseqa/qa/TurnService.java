package com.courseqa.qa;

import com.courseqa.common.CourseLocks;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.*;
import com.courseqa.db.WriteTransactions;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TurnService {
    record Submission(Turn turn, boolean created) {}
    private final ConversationRepository conversations;
    private final ConversationService scope;
    private final TurnRepository turns;
    private final FeedbackRepository feedback;
    private final CourseLocks locks;
    private final WriteTransactions writes;
    private final ExecutorService workers;
    private final TurnWorker worker;
    private final ModelSettings models;

    public TurnService(ConversationRepository conversations, ConversationService scope, TurnRepository turns,
            FeedbackRepository feedback, CourseLocks locks, WriteTransactions writes, TurnWorker worker,
            @Qualifier("qaWorkers") ExecutorService workers, @Value("${app.model.generation-model}") String generation,
            @Value("${app.model.embedding-model}") String embedding, @Value("${app.model.embedding-dimensions}") int dims) {
        this.conversations = conversations;
        this.scope = scope;
        this.turns = turns;
        this.feedback = feedback;
        this.locks = locks;
        this.writes = writes;
        this.worker = worker;
        this.workers = workers;
        models = new ModelSettings(generation, null, embedding, null, dims, 0.1, 2048, false);
    }

    @PostConstruct
    void recover() { turns.stop(null, "interrupted", TurnRepository.interrupted(), null); }

    Submission create(UUID conversation, TurnCreate input) { return submit(conversation, input, null); }

    Submission retry(UUID id, UUID request) {
        var original = turns.entry(id).turn();
        return submit(original.conversationId(), new TurnCreate(request, original.question(), original.strategy(),
                original.topK()), id);
    }

    private Submission submit(UUID conversation, TurnCreate input, UUID original) {
        UUID course = conversations.get(conversation).knowledgeBaseId();
        Submission submitted = locks.withLock(course, () -> writes.execute(() -> {
            var current = conversations.get(conversation);
            var existing = turns.byRequest(conversation, input.clientRequestId());
            if (existing != null) {
                var t = existing.turn();
                if (!Objects.equals(original, t.retryOfTurnId()) || !t.question().equals(input.question())
                        || !t.strategy().equals(input.strategy()) || !t.topK().equals(input.topK())) {
                    throw new ServiceException("IDEMPOTENCY_CONFLICT", 409, "该请求编号已用于另一项问答操作", false);
                }
                return new Submission(turns.snapshot(t.id()), false);
            }
            var old = original == null ? null : turns.entry(original);
            if (old != null && (!("failed".equals(old.turn().status()) || "interrupted".equals(old.turn().status()))
                    || old.turn().error() == null || !Boolean.TRUE.equals(old.turn().error().retryable()))) {
                throw new ServiceException("TURN_NOT_RETRYABLE", 409, "仅允许重试的失败或中断问答可执行此操作", false);
            }
            if (current.activeTurnId() != null) throw QaRequests.busy();
            List<UUID> fixed = old == null ? current.documentIds() : old.documents();
            scope.scope().validateFixed(course, fixed);
            var history = old == null ? turns.history(conversation) : old.history();
            return new Submission(turns.insert(conversation, input, original, fixed, history, models), true);
        }));
        if (!submitted.created()) return new Submission(availability(submitted.turn()), false);
        UUID id = submitted.turn().id();
        // The write transaction has committed. An idempotent replay must never enqueue a second job.
        try {
            workers.execute(() -> worker.execute(id));
        } catch (RejectedExecutionException ex) {
            turns.stop(id, "interrupted", TurnRepository.interrupted(), null);
            return new Submission(turns.snapshot(id), true);
        }
        return submitted;
    }

    Feedback feedback(UUID id, FeedbackWrite input) {
        return writes.execute(() -> {
            if (!"answered".equals(turns.entry(id).turn().status())) throw QaRequests.invalid("仅可对已回答的问题提交反馈");
            return feedback.put(id, input);
        });
    }

    Turn get(UUID id) { return availability(turns.snapshot(id)); }

    TurnList list(UUID conversation, QaRequests.Page page) {
        var result = turns.list(conversation, page);
        var ids = result.items().stream().flatMap(t -> t.citations().stream()).map(Citation::documentId).distinct().toList();
        var available = scope.availability(ids);
        return new TurnList(result.items().stream().map(t -> annotate(t, available)).toList(),
                result.page(), result.pageSize(), result.total());
    }

    private Turn availability(Turn t) {
        return annotate(t, scope.availability(t.citations().stream().map(Citation::documentId).distinct().toList()));
    }

    private static Turn annotate(Turn t, Map<UUID, Boolean> available) {
        if (t.citations().isEmpty()) return t;
        var citations = t.citations().stream().map(c -> new Citation(c.citationId(), c.documentId(), c.chunkId(),
                c.fileName(), c.pageNo(), c.excerpt(), Boolean.TRUE.equals(available.get(c.documentId())))).toList();
        return TurnRepository.withDetails(t, citations, t.feedback());
    }
}
