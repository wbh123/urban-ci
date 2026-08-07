package org.urbansafe.priority.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.urbansafe.priority.persistence.entity.OperationLogEntity;
import org.urbansafe.priority.persistence.mapper.OperationLogMapper;
import org.urbansafe.priority.persistence.mapperext.OperationLogMapperExt;

/**
 * 审计日志持久化仓库，隔离生成 Mapper 与业务服务。
 */
@Repository
public class OperationLogRepository {

    private final OperationLogMapper operationLogMapper;

    /**
     * 审计日志专用写入 Mapper，负责 PostgreSQL INET 等无法由通用 Mapper 安全推断的类型转换。
     */
    private final OperationLogMapperExt operationLogMapperExt;

    /**
     * 创建审计日志仓库。
     *
     * @param operationLogMapper 自动生成的基础查询 Mapper
     * @param operationLogMapperExt 显式处理 PostgreSQL 专有类型的写入 Mapper
     */
    public OperationLogRepository(
            OperationLogMapper operationLogMapper,
            OperationLogMapperExt operationLogMapperExt) {
        this.operationLogMapper = operationLogMapper;
        this.operationLogMapperExt = operationLogMapperExt;
    }

    /**
     * 追加审计日志。
     *
     * @param entity 仅由统一审计服务构造的持久化实体
     * @return 数据库影响行数
     */
    public int insert(OperationLogEntity entity) {
        return operationLogMapperExt.insert(entity);
    }

    /**
     * 按审计筛选条件分页查询。
     *
     * @param requestId 请求编号
     * @param action 操作类型
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param page 已由公共分页适配层创建的一基 MyBatis-Plus 分页对象
     * @return 按操作时间倒序排列的分页结果
     */
    public Page<OperationLogEntity> page(
            String requestId,
            String action,
            String resourceType,
            UUID resourceId,
            Page<OperationLogEntity> page) {
        LambdaQueryWrapper<OperationLogEntity> query = new LambdaQueryWrapper<>();
        query.eq(requestId != null, OperationLogEntity::getRequestId, requestId);
        query.eq(action != null, OperationLogEntity::getOperationType, action);
        query.eq(resourceType != null, OperationLogEntity::getResourceType, resourceType);
        query.eq(resourceId != null, OperationLogEntity::getResourceId, resourceId);
        query.orderByDesc(OperationLogEntity::getOperatedAt);
        return operationLogMapper.selectPage(page, query);
    }

    /**
     * 按主键查询单条审计日志。
     *
     * @param operationId 审计日志标识
     * @return 日志实体，不存在时返回空
     */
    public OperationLogEntity findById(UUID operationId) {
        return operationLogMapper.selectById(operationId);
    }
}
