package org.urbansafe.priority.common.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.building.result.BuildingListResult;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.community.result.CommunityListResult;

/**
 * 受限角色的基础档案查询。
 *
 * <p>授权小区集合直接进入 SQL 的 WHERE 条件，保证分页条数、总数和页面内容都不包含
 * 越权对象。全局角色继续复用既有业务 Service。</p>
 */
@Service
public class ScopedArchiveQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public ScopedArchiveQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<CommunityListResult> listCommunities(
            String keyword,
            String administrativeRegion,
            String status,
            ApiPageRequest pageRequest,
            String sort,
            CommunityAccessScope scope) {
        if (!scope.global() && scope.communityIds().isEmpty()) {
            return emptyPage(pageRequest);
        }

        MapSqlParameterSource params = pageParams(pageRequest);
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL");
        appendScope(where, params, "id", scope);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (community_code ILIKE :keyword"
                    + " OR community_name ILIKE :keyword OR address ILIKE :keyword)");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (administrativeRegion != null && !administrativeRegion.isBlank()) {
            where.append(" AND administrative_region=:administrativeRegion");
            params.addValue("administrativeRegion", administrativeRegion.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status=:status");
            params.addValue("status", status.trim());
        }

        String orderBy = communityOrderBy(sort);
        long total = count("SELECT COUNT(*) FROM core.community" + where, params);
        List<CommunityListResult> content = jdbc.query("""
                SELECT id, community_code, community_name, administrative_region,
                       address, building_count, household_count, resident_count,
                       status, created_at, updated_at
                FROM core.community
                """ + where + orderBy + " OFFSET :offset LIMIT :size", params,
                new CommunityRowMapper());
        return page(content, pageRequest, total);
    }

    public PageResult<BuildingListResult> listBuildings(
            UUID communityId,
            String keyword,
            ApiPageRequest pageRequest,
            String sort,
            CommunityAccessScope scope) {
        if (!scope.global() && scope.communityIds().isEmpty()) {
            return emptyPage(pageRequest);
        }
        if (communityId != null && !scope.allows(communityId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "BUSINESS_ARCHIVE_ACCESS_DENIED");
        }

        MapSqlParameterSource params = pageParams(pageRequest);
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL");
        appendScope(where, params, "community_id", scope);
        if (communityId != null) {
            where.append(" AND community_id=:requestedCommunityId");
            params.addValue("requestedCommunityId", communityId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (building_code ILIKE :keyword"
                    + " OR building_name ILIKE :keyword OR address ILIKE :keyword)");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }

        String orderBy = buildingOrderBy(sort);
        long total = count("SELECT COUNT(*) FROM core.building" + where, params);
        List<BuildingListResult> content = jdbc.query("""
                SELECT id, community_id, building_code, building_name,
                       construction_year, floor_count, resident_count, status, created_at
                FROM core.building
                """ + where + orderBy + " OFFSET :offset LIMIT :size", params,
                new BuildingRowMapper());
        return page(content, pageRequest, total);
    }

    private void appendScope(
            StringBuilder where,
            MapSqlParameterSource params,
            String column,
            CommunityAccessScope scope) {
        if (!scope.global()) {
            where.append(" AND ").append(column).append(" IN (:authorizedCommunityIds)");
            params.addValue("authorizedCommunityIds", scope.communityIds());
        }
    }

    private MapSqlParameterSource pageParams(ApiPageRequest request) {
        return new MapSqlParameterSource()
                .addValue("offset", (long) request.page() * request.size())
                .addValue("size", request.size());
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private String communityOrderBy(String sort) {
        SortValue value = parseSort(sort, "createdAt", false);
        String column = switch (value.field()) {
            case "communityCode" -> "community_code";
            case "communityName" -> "community_name";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            default -> throw new InvalidRequestException(
                    "INVALID_SORT_FIELD", "不支持的小区排序字段: " + value.field());
        };
        return " ORDER BY " + column + (value.ascending() ? " ASC" : " DESC") + ", id ASC";
    }

    private String buildingOrderBy(String sort) {
        SortValue value = parseSort(sort, "createdAt", false);
        String column = switch (value.field()) {
            case "constructionYear" -> "construction_year";
            case "floorCount" -> "floor_count";
            case "residentCount" -> "resident_count";
            case "createdAt" -> "created_at";
            default -> throw new InvalidRequestException(
                    "INVALID_SORT_FIELD", "不支持的楼栋排序字段: " + value.field());
        };
        return " ORDER BY " + column + (value.ascending() ? " ASC" : " DESC")
                + " NULLS LAST, id ASC";
    }

    private SortValue parseSort(String sort, String defaultField, boolean defaultAscending) {
        if (sort == null || sort.isBlank()) {
            return new SortValue(defaultField, defaultAscending);
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new InvalidRequestException(
                    "INVALID_SORT_FIELD", "排序格式必须为 field,asc 或 field,desc");
        }
        boolean ascending = parts.length == 1 || "asc".equalsIgnoreCase(parts[1]);
        if (parts.length == 2
                && !"asc".equalsIgnoreCase(parts[1])
                && !"desc".equalsIgnoreCase(parts[1])) {
            throw new InvalidRequestException(
                    "INVALID_SORT_FIELD", "排序格式必须为 field,asc 或 field,desc");
        }
        return new SortValue(parts[0], ascending);
    }

    private <T> PageResult<T> page(List<T> content, ApiPageRequest request, long total) {
        long totalPages = total == 0 ? 0 : (total + request.size() - 1) / request.size();
        return new PageResult<>(content, request.page(), request.size(), total, totalPages);
    }

    private <T> PageResult<T> emptyPage(ApiPageRequest request) {
        return new PageResult<>(List.of(), request.page(), request.size(), 0, 0);
    }

    private record SortValue(String field, boolean ascending) {
    }

    private static final class CommunityRowMapper implements RowMapper<CommunityListResult> {
        @Override
        public CommunityListResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CommunityListResult(
                    rs.getObject("id", UUID.class),
                    rs.getString("community_code"),
                    rs.getString("community_name"),
                    rs.getString("administrative_region"),
                    rs.getString("address"),
                    nullableInteger(rs, "building_count"),
                    nullableInteger(rs, "household_count"),
                    nullableInteger(rs, "resident_count"),
                    rs.getString("status"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class));
        }
    }

    private static final class BuildingRowMapper implements RowMapper<BuildingListResult> {
        @Override
        public BuildingListResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new BuildingListResult(
                    rs.getObject("id", UUID.class),
                    rs.getObject("community_id", UUID.class),
                    rs.getString("building_code"),
                    rs.getString("building_name"),
                    nullableInteger(rs, "construction_year"),
                    nullableInteger(rs, "floor_count"),
                    nullableInteger(rs, "resident_count"),
                    rs.getString("status"),
                    rs.getObject("created_at", OffsetDateTime.class));
        }
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
