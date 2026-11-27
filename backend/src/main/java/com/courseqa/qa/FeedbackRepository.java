package com.courseqa.qa;

import com.courseqa.contracts.dto.Feedback;
import com.courseqa.contracts.dto.FeedbackWrite;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {
    private final JdbcTemplate jdbc;
    public FeedbackRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Feedback get(UUID turnId) {
        return jdbc.query("SELECT * FROM feedback WHERE turn_id=?", (rs, i) ->
                new Feedback(QaData.id(rs, "id"), turnId, rs.getString("rating"), rs.getString("reason"),
                        rs.getString("comment"), QaData.time(rs, "created_at"), QaData.time(rs, "updated_at")),
                turnId.toString()).stream().findFirst().orElse(null);
    }

    Feedback put(UUID turnId, FeedbackWrite input) {
        String now = QaData.now();
        jdbc.update("""
                INSERT INTO feedback(id,turn_id,rating,reason,comment,created_at,updated_at) VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(turn_id) DO UPDATE SET rating=excluded.rating,reason=excluded.reason,
                   comment=excluded.comment,updated_at=excluded.updated_at
                """, UUID.randomUUID().toString(), turnId.toString(), input.rating(), input.reason(), input.comment(),
                now, now);
        return get(turnId);
    }
}
