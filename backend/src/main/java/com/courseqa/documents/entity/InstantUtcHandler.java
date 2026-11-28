// 负责人 B。B 自有实体的 UTC 时间戳处理器：与 D 模块 QaData 的约定一致，
// 数据库存 ISO-8601 UTC 文本（appendInstant(9)），读回用 Instant.parse。
// 不使用 MyBatis 默认 InstantTypeHandler，后者在 sqlite-jdbc 下会写成 epoch 毫秒，
// 读回时又按日期格式解析该数字而失败。
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
