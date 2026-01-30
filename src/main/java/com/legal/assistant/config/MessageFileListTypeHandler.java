package com.legal.assistant.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.dto.response.MessageFileItem;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis TypeHandler：message.files 列（TEXT/JSON 字符串）与 List&lt;MessageFileItem&gt; 互转，
 * 查询时自动反序列化为对象列表，返回给前端时无需 JSON.parse。
 * 仅通过 Message 实体的 @TableField(typeHandler = MessageFileListTypeHandler.class) 绑定，不全局注册。
 */
public class MessageFileListTypeHandler extends BaseTypeHandler<List<MessageFileItem>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<MessageFileItem>> TYPE_REF = new TypeReference<List<MessageFileItem>>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<MessageFileItem> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, parameter == null || parameter.isEmpty() ? null : OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("序列化 message.files 失败", e);
        }
    }

    @Override
    public List<MessageFileItem> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<MessageFileItem> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<MessageFileItem> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static List<MessageFileItem> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<MessageFileItem> list = OBJECT_MAPPER.readValue(json, TYPE_REF);
            return list != null ? list : new ArrayList<>();
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
