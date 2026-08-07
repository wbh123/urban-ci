package org.urbansafe.priority.persistence.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** 统一填充 UUID 主键、createdAt 和 updatedAt，数据库中保存带时区时间。 */
@Component
public class PersistenceMetaObjectHandler implements MetaObjectHandler {

    /** 统一 UTC 时钟，由 server 的 TimeConfig 提供。 */
    private final Clock clock;

    /**
     * 创建自动填充处理器。
     *
     * @param clock 应用统一时钟
     */
    public PersistenceMetaObjectHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * 插入前补齐 UUID、createdAt 与 updatedAt；已有值保持不变。
     *
     * @param metaObject MyBatis 当前实体元数据
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        if (getFieldValByName("id", metaObject) == null) {
            setFieldValByName("id", UUID.randomUUID(), metaObject);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (getFieldValByName("createdAt", metaObject) == null) {
            setFieldValByName("createdAt", now, metaObject);
        }
        if (getFieldValByName("updatedAt", metaObject) == null) {
            setFieldValByName("updatedAt", now, metaObject);
        }
    }

    /**
     * 更新前刷新 updatedAt。
     *
     * @param metaObject MyBatis 当前实体元数据
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", OffsetDateTime.now(clock), metaObject);
    }
}
