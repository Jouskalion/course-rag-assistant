package com.courseqa.qa;

import com.courseqa.common.FeatureNotImplementedException;
import com.courseqa.common.ModelResponseException;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.contracts.port.ChatProvider;
import com.courseqa.contracts.port.Retriever;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TurnWorker {
    private static final Logger LOG = LoggerFactory.getLogger(TurnWorker.class);
    private final TurnRepository turns;
    private final Retriever retriever;
    private final ChatProvider chats;
    private final ModelAnswerValidator validator;
    private final CitationMapper citations;
    private final ScheduledExecutorService timers;

    public TurnWorker(TurnRepository turns, Retriever retriever, ChatProvider chats, ModelAnswerValidator validator,
            CitationMapper citations, @Qualifier("qaDeadlines") ScheduledExecutorService timers) {
        this.turns = turns;
        this.retriever = retriever;
        this.chats = chats;
        this.validator = validator;
        this.citations = citations;
        this.timers = timers;
    }

    public void execute(UUID turnId) {
        long started = System.nanoTime();
        var deadline = new Deadline(started + TimeUnit.SECONDS.toNanos(60));
        var guard = new RunningThread(Thread.currentThread());
        var alarm = timers.schedule(() -> {
            if (guard.interrupt()) {
                turns.stop(turnId, "failed", TurnRepository.timeout(), (System.nanoTime() - started) / 1_000_000.0);
            }
        }, Math.max(0, deadline.deadlineNanos() - System.nanoTime()), TimeUnit.NANOSECONDS);
        try {
            var entry = turns.entry(turnId);
            if ("queued".equals(entry.turn().status())) new Run(entry, started, deadline).process();
        } finally {
            guard.release();
            alarm.cancel(false);
        }
    }

    /** The timer must never interrupt the next task on the same pool thread. */
    private static final class RunningThread {
        private Thread thread;
        RunningThread(Thread thread) { this.thread = thread; }
        synchronized boolean interrupt() {
            if (thread == null) return false;
            thread.interrupt();
            return true;
        }
        synchronized void release() { thread = null; }
    }

    private final class Run {
        final TurnRepository.Entry entry;
        final Turn turn;
        final TurnProgress progress;
        final Deadline deadline;
        String stage = "queued";
        String standalone;
        SearchResult evidence;

        Run(TurnRepository.Entry entry, long started, Deadline deadline) {
            this.entry = entry;
            turn = entry.turn();
            this.deadline = deadline;
            progress = new TurnProgress(turn.createdAt(), turn.modelSettings(), started);
        }

        void process() {
            try {
                if (!entry.history().isEmpty() || turns.isFollowup(turn.id())) {
                    stage("rewriting");
                    var rewrite = chats.rewrite(turn.question(), entry.history(), deadline);
                    progress.chat(true, rewrite.usage(), rewrite.actualModel());
                    if (rewrite.needsClarification()) {
                        finish("needs_clarification", rewrite.message(), null, null);
                        return;
                    }
                    standalone = rewrite.standaloneQuestion();
                } else {
                    standalone = turn.question();
                }
                stage("retrieving");
                var request = new SearchRequest(entry.course(), entry.documents(), standalone, turn.strategy(), turn.topK());
                evidence = retriever.search(request, deadline);
                progress.retrieval(evidence);
                if (evidence.hits().isEmpty()) {
                    finish("insufficient_evidence", "所选资料中未检索到可支持回答的片段。", null, null);
                    return;
                }
                stage("generating");
                var call = chats.generate(standalone, evidence.hits(), deadline);
                progress.chat(false, call.usage(), call.actualModel());
                stage("validating");
                ModelAnswer answer;
                try {
                    answer = validator.validate(call.content(), evidence);
                } catch (ServiceException invalid) {
                    stage("generating");
                    var repaired = chats.repair(standalone, evidence.hits(), call.content(), invalid.getMessage(), deadline);
                    progress.chat(false, repaired.usage(), repaired.actualModel());
                    stage("validating");
                    answer = validator.validate(repaired.content(), evidence);
                }
                finish(answer.answerStatus(), answer.message(), null, citations.bind(answer, evidence));
            } catch (TurnRepository.StateChanged ex) {
                // Another execution or the deadline has already taken ownership of this row.
                return;
            } catch (CancellationException ex) {
                fail(TurnRepository.interrupted());
            } catch (FeatureNotImplementedException ex) {
                if ("retrieving".equals(stage)) progress.noVectorCall();
                fail(new ServiceException("FEATURE_NOT_IMPLEMENTED", 501, ex.getMessage(), false));
            } catch (ServiceException ex) {
                if (ex instanceof ModelResponseException response) {
                    progress.chat("rewriting".equals(stage), response.usage(), response.actualModel());
                } else if ("MODEL_NOT_CONFIGURED".equals(ex.asError().code())) {
                    if ("retrieving".equals(stage)) progress.noVectorCall();
                    else progress.noModelCall("rewriting".equals(stage));
                } else if ("retrieving".equals(stage) && Set.of("EMPTY_SCOPE", "SCOPE_MISMATCH",
                        "SOURCE_UNAVAILABLE", "NOT_FOUND", "INVALID_REQUEST").contains(ex.asError().code())) {
                    progress.noVectorCall();
                }
                fail(ex);
            } catch (RuntimeException ex) {
                LOG.error("问答 {} 执行失败", turn.id(), ex);
                fail(new ServiceException("INTERNAL_ERROR", 500, "问答处理失败，请查看后端日志", false));
            }
        }

        private void stage(String next) {
            TurnRepository.checkTime(deadline);
            progress.begin(next, turn.strategy());
            turns.stage(turn.id(), stage, next, standalone, evidence, progress, deadline);
            stage = next;
        }

        private void fail(ServiceException error) {
            finish("TASK_INTERRUPTED".equals(error.asError().code()) ? "interrupted" : "failed", null, error.asError(), null);
        }

        private void finish(String status, String message, ApiError error, CitationMapper.BoundAnswer answer) {
            progress.end();
            turns.finish(entry, status, standalone, evidence, message, error, answer, progress, deadline);
        }
    }
}
