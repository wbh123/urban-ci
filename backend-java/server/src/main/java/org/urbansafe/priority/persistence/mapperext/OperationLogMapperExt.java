package org.urbansafe.priority.persistence.mapperext;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.urbansafe.priority.persistence.entity.OperationLogEntity;

/**
 * 审计日志数据库特性扩展 Mapper。
 *
 * <p>{@code audit.operation_log.client_ip} 使用 PostgreSQL 专有的 {@code INET} 类型，而自动生成实体为了
 * 保持业务层易用性将其表达为字符串。该扩展 Mapper 只承载必须显式进行数据库类型转换的写入语句；普通查询
 * 继续复用自动生成的 {@code OperationLogMapper}，避免把数据库特性散落到业务服务中。
 */
@Mapper
public interface OperationLogMapperExt {

    /**
     * 追加一条不可修改、不可删除的审计证据链记录。
     *
     * <p>对应 XML 会将 {@link OperationLogEntity#getClientIp()} 显式转换为 PostgreSQL {@code INET}，
     * 并通过统一 JSONB 类型处理器写入四个 JSON 字段。
     *
     * @param entity 统一审计服务已经完成脱敏和字段补全的日志实体
     * @return 数据库实际插入的行数，正常情况下固定为 1
     */
    int insert(@Param("entity") OperationLogEntity entity);
}
