package com.courseqa.qa;

import com.courseqa.common.ModelJson;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.UUID;

final class QaData {
    private QaData() {}
    static final String ACTIVE = "('queued','rewriting','retrieving','generating','validating')";
    // A fixed UTC precision keeps SQLite TEXT ordering chronological, including whole seconds.
    private static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();
    static String now() { return UTC.format(Instant.now()); }
    static String json(Object value) { return ModelJson.MAPPER.writeValueAsString(value); }
    static String nullableJson(Object value) { return value == null ? null : json(value); }
    static <T> T read(String value, Class<T> type) {
        return value == null ? null : ModelJson.MAPPER.readValue(value, type);
    }
    static <T> List<T> list(String value, Class<T> type) {
        var mapper = ModelJson.MAPPER;
        return mapper.readValue(value, mapper.getTypeFactory().constructCollectionType(List.class, type));
    }
    static UUID id(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }
    static Instant time(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Instant.parse(value);
    }
}
