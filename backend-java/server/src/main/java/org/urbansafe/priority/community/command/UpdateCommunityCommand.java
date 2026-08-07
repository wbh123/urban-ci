package org.urbansafe.priority.community.command;

/**
 * 修改小区的业务命令；{@code version} 是必须参与并发校验的客户端版本。
 *
 * @param version 客户端最后读取的乐观锁版本号
 * @param communityCode 小区业务编码
 * @param communityName 小区名称
 * @param administrativeRegion 行政区域
 * @param address 详细地址
 * @param constructionPeriod 建成年代说明
 * @param householdCount 户数
 * @param residentCount 居民数
 * @param status 业务状态
 * @param extraAttributes 扩展属性
 * @param remark 备注
 */
public record UpdateCommunityCommand(
        Long version,
        String communityCode,
        String communityName,
        String administrativeRegion,
        String address,
        String constructionPeriod,
        Integer householdCount,
        Integer residentCount,
        String status,
        Object extraAttributes,
        String remark) {
}
