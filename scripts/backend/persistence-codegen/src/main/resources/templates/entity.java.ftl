package org.urbansafe.priority.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Generated;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.urbansafe.priority.persistence.typehandler.JsonNodeTypeHandler;

/** ${schemaName}.${tableName} 表的自动生成持久化实体；禁止手工修改。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@TableName(value = "${tableName}", schema = "${schemaName}"<#if autoResultMap>, autoResultMap = true</#if>)
public class ${className} {

<#list columns as column>
<#-- ColumnDefinition 是公开 JavaBean；使用布尔属性读取以兼容 FreeMarker 严格包装器。 -->
<#if column.id>
    @TableId(type = IdType.INPUT)
<#elseif column.logicDelete>
    @TableLogic(value = "null", delval = "now()")
<#elseif column.version>
    @Version
<#elseif column.json>
    @TableField(typeHandler = JsonNodeTypeHandler.class)
<#elseif column.createdAt>
    @TableField(fill = FieldFill.INSERT)
<#elseif column.updatedAt>
    @TableField(fill = FieldFill.INSERT_UPDATE)
</#if>
    /** 数据库字段 `${column.columnName}` 映射的 Java 属性。 */
    private ${column.javaType} ${column.propertyName};

</#list>
<#list columns as column>
    /**
     * 读取数据库字段 `${column.columnName}` 对应的属性值。
     *
     * @return `${column.columnName}` 的当前值
     */
    public ${column.javaType} get${column.propertyName?cap_first}() {
        return ${column.propertyName};
    }

    /**
     * 设置数据库字段 `${column.columnName}` 对应的属性值。
     *
     * @param ${column.propertyName} 写入 `${column.columnName}` 的值
     */
    public void set${column.propertyName?cap_first}(${column.javaType} ${column.propertyName}) {
        this.${column.propertyName} = ${column.propertyName};
    }

</#list>
}
