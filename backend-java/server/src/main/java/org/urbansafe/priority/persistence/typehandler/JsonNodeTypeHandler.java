package org.urbansafe.priority.persistence.typehandler;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL JSONB 与 Jackson JsonNode 的统一类型处理器。
 *
 * <p>MyBatis-Plus 的 JacksonTypeHandler 负责严格的 JSON 序列化与反序列化；
 * 本类在写入时额外包装为 PostgreSQL {@link PGobject}，明确指定数据库类型为 jsonb，
 * 避免驱动把 JSON 文本误当成普通 VARCHAR。解析失败会由父类抛出异常，不返回静默 null。
 */
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class JsonNodeTypeHandler extends JacksonTypeHandler {

    /**
     * MyBatis 扫描 {@code type-handlers-package} 时使用的无参构造器。
     *
     * <p>实体字段已经通过 {@code @TableField(typeHandler = JsonNodeTypeHandler.class)} 明确限定为
     * {@link JsonNode}，因此包扫描阶段可安全地把目标类型固定为 {@link JsonNode}。保留该构造器还能避免
     * MyBatis 在启动阶段因无法反射实例化处理器而中止整个 {@code SqlSessionFactory} 创建流程。
     */
    public JsonNodeTypeHandler() {
        super(JsonNode.class);
    }

    /**
     * MyBatis 通过 Java 类型创建 handler 时使用的构造器。
     *
     * @param type 字段 Java 类型，生成实体固定为 JsonNode
     */
    public JsonNodeTypeHandler(Class<?> type) {
        super(type);
    }

    /**
     * MyBatis-Plus 读取实体字段泛型信息时使用的构造器。
     *
     * @param type 字段 Java 类型
     * @param field 实体反射字段
     */
    public JsonNodeTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    /**
     * 将非空 JsonNode 作为 PostgreSQL jsonb 参数写入预编译语句。
     *
     * @param statement JDBC 预编译语句
     * @param parameter 待写入 JsonNode
     * @param index 参数位置
     * @param jdbcType MyBatis 推断的 JDBC 类型
     * @throws SQLException PostgreSQL 参数绑定失败
     */
    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            Object parameter,
            JdbcType jdbcType) throws SQLException {
        if (!(parameter instanceof JsonNode jsonNode)) {
            throw new SQLException("JsonNodeTypeHandler 只接受 JsonNode 参数");
        }
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(toJson(jsonNode));
        statement.setObject(index, jsonb);
    }
}
