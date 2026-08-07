package org.urbansafe.priority.community.result;

import java.util.List;

/**
 * 小区分页业务结果。
 *
 * @param content 当前页小区列表
 * @param page 当前页码
 * @param size 每页数量
 * @param totalElements 总记录数
 * @param totalPages 总页数
 */
public record CommunityPageResult(
        List<CommunityListResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
