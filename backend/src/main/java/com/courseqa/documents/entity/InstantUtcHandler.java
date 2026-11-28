// 负责人 B。统一存 ISO-8601 UTC 文本；MP 默认 InstantTypeHandler 在 sqlite-jdbc 下读写格式不对称。
package com.courseqa.documents.entity;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
@MappedTypes(Instant.class)
public class InstantUtcHandler extends BaseTypeHandler<Instant> {
    private static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, UTC.format(parameter));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Instant getNullableResult(java.sql.CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Instant parse(String value) { return value == null ? null : Instant.parse(value); }
}
