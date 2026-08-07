package org.urbansafe.priority.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.annotation.Generated;
import org.apache.ibatis.annotations.Mapper;
import org.urbansafe.priority.persistence.entity.${className};

/** ${schemaName}.${tableName} 表的自动生成基础 Mapper；禁止写入自定义 SQL。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@Mapper
public interface ${mapperName} extends BaseMapper<${className}> {
}
