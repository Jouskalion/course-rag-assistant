package com.courseqa.qa;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.internal.HistoryTurn;
import com.courseqa.db.WriteTransactions;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TurnRepository {
    record Entry(Turn turn, UUID course, List<UUID> documents, List<HistoryTurn> history) {}
    static final class StateChanged extends RuntimeException {}
    private static final String SELECT = """
            SELECT t.*, c.knowledge_base_id AS course FROM turns t JOIN conversations c ON c.id=t.conversation_id
            """;
    private final JdbcTemplate jdbc;
    private final WriteTransactions writes;
    private final FeedbackRepository feedback;

    public TurnRepository(JdbcTemplate jdbc, WriteTransactions writes, FeedbackRepository feedback) {
        this.jdbc = jdbc;
        this.writes = writes;
        this.feedback = feedback;
    }

    Entry entry(UUID id) {
        return jdbc.query(SELECT + " WHERE t.id=?", this::row, id.toString()).stream().findFirst()
                .orElseThrow(() -> QaRequests.missing("问答"));
    }

    Entry byRequest(UUID conversation, UUID request) {
        return jdbc.query(SELECT + " WHERE t.conversation_id=? AND t.client_request_id=?", this::row,
                conversation.toString(), request.toString()).stream().findFirst().orElse(null);
    }

    private Entry row(ResultSet rs, int index) throws SQLException {
        var turn = new Turn(QaData.id(rs, "id"), QaData.id(rs, "conversation_id"), QaData.id(rs, "client_request_id"),
                QaData.id(rs, "retry_of_turn_id"), rs.getString("question"), rs.getString("standalone_question"),
                rs.getString("strategy"), rs.getInt("top_k"), rs.getString("status"),
                QaData.list(rs.getString("answer_blocks_json"), AnswerBlock.class), List.of(), rs.getString("message"),
                QaData.read(rs.getString("error_json"), ApiError.class),
                QaData.read(rs.getString("retrieval_snapshot_json"), SearchResult.class),
                QaData.read(rs.getString("timings_json"), Timings.class),
                QaData.read(rs.getString("token_usage_json"), TokenUsage.class),
                QaData.read(rs.getString("model_settings_json"), ModelSettings.class), null, QaData.time(rs, "created_at"),
                QaData.time(rs, "updated_at"), QaData.time(rs, "finished_at"));
        return new Entry(turn, QaData.id(rs, "course"), QaData.list(rs.getString("scope_document_ids_json"), UUID.class),
                QaData.list(rs.getString("rewrite_history_json"), HistoryTurn.class));
    }

    List<HistoryTurn> history(UUID conversation) {
        var recent = jdbc.query("""
                SELECT question,answer_blocks_json FROM turns WHERE conversation_id=? AND status='answered'
                ORDER BY created_at DESC,id DESC LIMIT 2
                """, (rs, index) -> {
            var blocks = QaData.list(rs.getString("answer_blocks_json"), AnswerBlock.class);
            return new HistoryTurn(rs.getString("question"), String.join("\n", blocks.stream().map(AnswerBlock::text).toList()));
        }, conversation.toString());
        var kept = new ArrayList<HistoryTurn>();
        int length = 0;
        for (var turn : recent) {
            length += turn.question().codePointCount(0, turn.question().length())
                    + turn.answer().codePointCount(0, turn.answer().length());
            if (length > 3000) break;
            kept.addFirst(turn);
        }
        return List.copyOf(kept);
    }

    boolean isFollowup(UUID turn) {
        // Empty retained history can also mean a later question whose history exceeded the budget.
        // A retry keeps the original question's position, even after other questions have been asked.
        return jdbc.queryForObject("""
                WITH RECURSIVE origin(id,conversation_id,created_at,retry_of_turn_id) AS (
                  SELECT id,conversation_id,created_at,retry_of_turn_id FROM turns WHERE id=?
                  UNION ALL
                  SELECT t.id,t.conversation_id,t.created_at,t.retry_of_turn_id
                    FROM turns t JOIN origin o ON t.id=o.retry_of_turn_id
                )
                SELECT EXISTS(SELECT 1 FROM origin o JOIN turns t ON t.conversation_id=o.conversation_id
                  WHERE o.retry_of_turn_id IS NULL AND (t.created_at,t.id)<(o.created_at,o.id))
                """, Integer.class, turn.toString()) == 1;
    }

    Turn insert(UUID conversation, TurnCreate input, UUID original, List<UUID> scope,
            List<HistoryTurn> history, ModelSettings models) {
        UUID id = UUID.randomUUID();
        String now = QaData.now();
        jdbc.update("""
                INSERT INTO turns(id,conversation_id,client_request_id,retry_of_turn_id,question,strategy,top_k,
                   scope_document_ids_json,rewrite_history_json,status,answer_blocks_json,timings_json,token_usage_json,
                   model_settings_json,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,'queued','[]',?,?,?,?,?)
                """, id.toString(), conversation.toString(), input.clientRequestId().toString(),
                original == null ? null : original.toString(), input.question(), input.strategy(), input.topK(),
                QaData.json(scope), QaData.json(history), QaData.json(TurnProgress.QUEUED),
                QaData.json(new TokenUsage(null,null,null,null,null,null)), QaData.json(models), now, now);
        touch(conversation, now);
        return entry(id).turn();
    }

    @Transactional(readOnly = true)
    public Turn snapshot(UUID id) { return hydrate(entry(id).turn()); }

    @Transactional(readOnly = true)
    public TurnList list(UUID conversation, QaRequests.Page page) {
        if (jdbc.queryForObject("SELECT count(*) FROM conversations WHERE id=?", Integer.class, conversation.toString()) == 0) {
            throw QaRequests.missing("会话");
        }
        var rows = jdbc.query(SELECT + " WHERE t.conversation_id=? ORDER BY t.created_at,t.id LIMIT ? OFFSET ?",
                this::row, conversation.toString(), page.size(), page.offset());
        int total = jdbc.queryForObject("SELECT count(*) FROM turns WHERE conversation_id=?", Integer.class, conversation.toString());
        return new TurnList(rows.stream().map(e -> hydrate(e.turn())).toList(), page.number(), page.size(), total);
    }

    private Turn hydrate(Turn base) {
        if (!"answered".equals(base.status())) return base;
        var citations = jdbc.query("SELECT * FROM citations WHERE turn_id=? ORDER BY citation_id", (rs, i) ->
                new Citation(rs.getInt("citation_id"), QaData.id(rs, "document_id"), QaData.id(rs, "chunk_id"),
                        rs.getString("file_name"), rs.getInt("page_no"), rs.getString("excerpt"), false), base.id().toString());
        return withDetails(base, citations, feedback.get(base.id()));
    }

    static Turn withDetails(Turn t, List<Citation> citations, Feedback feedback) {
        return new Turn(t.id(), t.conversationId(), t.clientRequestId(), t.retryOfTurnId(), t.question(),
                t.standaloneQuestion(), t.strategy(), t.topK(), t.status(), t.answerBlocks(), citations, t.message(),
                t.error(), t.retrieval(), t.timings(), t.tokenUsage(), t.modelSettings(), feedback,
                t.createdAt(), t.updatedAt(), t.finishedAt());
    }

    void stage(UUID id, String expected, String next, String standalone, SearchResult result,
            TurnProgress progress, Deadline deadline) {
        writes.execute(() -> {
            checkTime(deadline);
            int changed = jdbc.update("""
                    UPDATE turns SET status=?,standalone_question=?,retrieval_snapshot_json=?,timings_json=?,
                      token_usage_json=?,model_settings_json=?,updated_at=? WHERE id=? AND status=?
                    """, next, standalone, QaData.nullableJson(result), QaData.json(progress.timings()),
                    QaData.json(progress.usage()), QaData.json(progress.models()), QaData.now(), id.toString(), expected);
            if (changed == 0) throw new StateChanged();
            return null;
        });
    }

    void finish(Entry entry, String status, String standalone, SearchResult result, String message, ApiError error,
            CitationMapper.BoundAnswer answer, TurnProgress progress, Deadline deadline) {
        writes.execute(() -> {
            // Recheck after waiting for the write lock, including on a late provider success.
            boolean expired = deadline.deadlineNanos() - System.nanoTime() <= 0;
            String now = QaData.now();
            List<AnswerBlock> blocks = expired || answer == null ? List.of() : answer.blocks();
            int changed = jdbc.update("""
                    UPDATE turns SET status=?,standalone_question=?,retrieval_snapshot_json=?,answer_blocks_json=?,
                      message=?,error_json=?,timings_json=?,token_usage_json=?,model_settings_json=?,updated_at=?,finished_at=?
                    WHERE id=? AND status IN
                    """ + QaData.ACTIVE, expired ? "failed" : status, standalone, QaData.nullableJson(result),
                    QaData.json(blocks), expired ? null : message, QaData.nullableJson(expired ? timeout().asError() : error),
                    QaData.json(progress.timings()), QaData.json(progress.usage()), QaData.json(progress.models()), now, now,
                    entry.turn().id().toString());
            if (changed == 0) return null;
            if (!blocks.isEmpty()) {
                for (var citation : answer.citations()) saveCitation(entry.turn().id(), citation);
            }
            touch(entry.turn().conversationId(), now);
            return null;
        });
    }

    private void saveCitation(UUID turn, CitationMapper.Snapshot c) {
        // Deletion may have cleaned this chunk since retrieval. The immutable PDF excerpt still belongs to this answer.
        jdbc.update("""
                INSERT INTO citations(id,turn_id,citation_id,document_id,chunk_id,file_name,page_no,excerpt)
                VALUES(?,?,?,?,(SELECT id FROM chunks WHERE id=? AND document_id=?),?,?,?)
                """, UUID.randomUUID().toString(), turn.toString(), c.citationId(), c.documentId().toString(),
                c.chunkId().toString(), c.documentId().toString(), c.fileName(), c.pageNo(), c.excerpt());
    }

    private void touch(UUID conversation, String now) {
        jdbc.update("UPDATE conversations SET updated_at=? WHERE id=?", now, conversation.toString());
    }

    void stop(UUID id, String status, ServiceException reason, Double executionMs) {
        writes.execute(() -> {
            String now = QaData.now();
            String predicate = id == null ? "" : " AND id=?";
            var args = new ArrayList<Object>();
            args.add(status); args.add(QaData.json(reason.asError())); args.add(QaData.json(TurnProgress.ZERO));
            args.add(executionMs); args.add(executionMs); args.add(now); args.add(now);
            if (id != null) args.add(id.toString());
            jdbc.update("UPDATE conversations SET updated_at=? WHERE id IN (SELECT conversation_id FROM turns WHERE status IN "
                    + QaData.ACTIVE + predicate + ")", id == null ? new Object[]{now} : new Object[]{now, id.toString()});
            return jdbc.update("""
                    UPDATE turns SET status=?,error_json=?,message=NULL,answer_blocks_json='[]',
                      token_usage_json=CASE WHEN status='queued' THEN ? ELSE token_usage_json END,
                      timings_json=CASE WHEN ? IS NULL THEN timings_json ELSE json_set(timings_json,'$.total_ms',?) END,
                      updated_at=?,finished_at=? WHERE status IN
                    """ + QaData.ACTIVE + predicate, args.toArray());
        });
    }

    static ServiceException timeout() { return new ServiceException("MODEL_TIMEOUT", 504, "本轮问答超过 60 秒执行期限", true); }
    static void checkTime(Deadline deadline) {
        if (deadline.deadlineNanos() - System.nanoTime() <= 0) throw timeout();
        if (Thread.currentThread().isInterrupted()) throw interrupted();
    }
    static ServiceException interrupted() {
        return new ServiceException("TASK_INTERRUPTED", 503, "问答已中断，可主动重试", true);
    }
}
