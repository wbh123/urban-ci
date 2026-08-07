package org.urbansafe.priority.persistence.mapperext;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 小区表的手写扩展 Mapper。
 *
 * <p>该接口只保存不能由基础 BaseMapper 表达的受控 SQL，避免重新生成
 * {@code CommunityMapper} 时覆盖自定义统计逻辑。
 */
@Mapper
public interface CommunityMapperExt {

    /**
     * 根据当前未删除楼栋重新计算小区 building_count。
     *
     * @param communityId 待刷新的小区 UUID
     * @return 更新行数，目标小区不存在时为 0
     */
    @Update("""
            UPDATE core.community
            SET building_count = (
                SELECT COUNT(*)
                FROM core.building b
                WHERE b.community_id = core.community.id
                  AND b.deleted_at IS NULL
            )
            WHERE id = #{communityId}
              AND deleted_at IS NULL
            """)
    int refreshBuildingCount(@Param("communityId") UUID communityId);
}
