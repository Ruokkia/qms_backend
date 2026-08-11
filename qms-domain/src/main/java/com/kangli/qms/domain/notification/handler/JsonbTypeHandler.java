package com.kangli.qms.domain.notification.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL jsonb 类型处理器：将 Java 端的 String（合法 JSON）与数据库 jsonb 列互通。
 * <p>解决 MyBatis-Plus 默认将 String 以 varchar 写入 jsonb 列导致的
 * "column is of type jsonb but expression is of type character varying" 错误。</p>
 */
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    private static final String JSONB_TYPE = "jsonb";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType(JSONB_TYPE);
        pgObject.setValue(parameter);
        ps.setObject(i, pgObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readJson(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readJson(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readJson(cs.getObject(columnIndex));
    }

    private String readJson(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof PGobject) {
            return ((PGobject) value).getValue();
        }
        return value.toString();
    }
}
