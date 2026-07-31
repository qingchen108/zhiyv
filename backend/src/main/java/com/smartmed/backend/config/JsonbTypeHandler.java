package com.smartmed.backend.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL JSONB 类型处理器（ADR-0012 配套）。
 * <p>
 * MyBatis-Plus 默认把 String 按 varchar 绑定，写入 jsonb 列时报
 * "column is of type jsonb but expression is of type character varying"。
 * 此 handler 用 {@code Types.OTHER} 绑定字符串，配合连接参数 {@code stringtype=unspecified}
 * 让 PG 驱动将其作为 jsonb 写入。读取时直接取字符串。
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(String.class)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
