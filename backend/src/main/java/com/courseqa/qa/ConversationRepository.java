package com.courseqa.qa;

import com.courseqa.contracts.dto.Conversation;
import com.courseqa.contracts.dto.ConversationCreate;
import com.courseqa.contracts.dto.ConversationList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConversationRepository {
    private static final String SELECT = """
            SELECT c.*,
              (SELECT t.id FROM turns t WHERE t.conversation_id=c.id AND t.status IN %s) AS active_id,
              (SELECT json_group_array(document_id) FROM
                 (SELECT document_id FROM conversation_documents WHERE conversation_id=c.id ORDER BY document_id)) AS ids
            FROM conversations c
            """.formatted(QaData.ACTIVE);
    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Conversation get(UUID id) {
        return jdbc.query(SELECT + " WHERE c.id=?", this::row, id.toString()).stream().findFirst()
                .orElseThrow(() -> QaRequests.missing("会话"));
    }

    @Transactional(readOnly = true)
    public ConversationList list(UUID course, QaRequests.Page page) {
        var items = jdbc.query(SELECT + " WHERE c.knowledge_base_id=? ORDER BY c.updated_at DESC,c.id LIMIT ? OFFSET ?",
                this::row, course.toString(), page.size(), page.offset());
        int total = jdbc.queryForObject("SELECT count(*) FROM conversations WHERE knowledge_base_id=?", Integer.class,
                course.toString());
        return new ConversationList(items, page.number(), page.size(), total);
    }

    private Conversation row(ResultSet rs, int index) throws SQLException {
        return new Conversation(QaData.id(rs, "id"), QaData.id(rs, "knowledge_base_id"), rs.getString("title"),
                rs.getString("scope"), QaData.list(rs.getString("ids"), UUID.class), List.of(), QaData.id(rs, "active_id"),
                QaData.time(rs, "created_at"), QaData.time(rs, "updated_at"));
    }

    Conversation insert(ConversationCreate input, List<UUID> documents) {
        UUID id = UUID.randomUUID();
        String now = QaData.now();
        jdbc.update("INSERT INTO conversations(id,knowledge_base_id,title,scope,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id.toString(), input.knowledgeBaseId().toString(), input.title(), input.scope(), now, now);
        jdbc.batchUpdate("INSERT INTO conversation_documents(conversation_id,document_id,knowledge_base_id) VALUES(?,?,?)",
                documents, 100, (ps, document) -> {
                    ps.setString(1, id.toString());
                    ps.setString(2, document.toString());
                    ps.setString(3, input.knowledgeBaseId().toString());
                });
        return get(id);
    }

    void rename(UUID id, String title) {
        jdbc.update("UPDATE conversations SET title=?, updated_at=? WHERE id=?", title, QaData.now(), id.toString());
    }

    void delete(UUID id) { jdbc.update("DELETE FROM conversations WHERE id=?", id.toString()); }
}
