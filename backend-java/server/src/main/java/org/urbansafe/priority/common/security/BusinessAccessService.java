package org.urbansafe.priority.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

/** 小区、楼栋等基础档案的统一角色和对象范围校验。 */
@Service
public class BusinessAccessService {

    public static final String DIRECTORY_READ_ROLES = "hasAnyRole('ADMIN','GOVERNMENT_MANAGER',"
            + "'COMMUNITY_MANAGER','EXPERT','PROFESSIONAL_REVIEWER')";
    public static final String COMMUNITY_CREATE_DELETE_ROLES =
            "hasAnyRole('ADMIN','GOVERNMENT_MANAGER')";
    public static final String ARCHIVE_MANAGE_ROLES =
            "hasAnyRole('ADMIN','GOVERNMENT_MANAGER','COMMUNITY_MANAGER')";

    private static final Set<String> GLOBAL_READ_ROLES = Set.of(
            "ADMIN", "GOVERNMENT_MANAGER", "EXPERT", "PROFESSIONAL_REVIEWER");
    private static final Set<String> GLOBAL_MANAGE_ROLES = Set.of(
            "ADMIN", "GOVERNMENT_MANAGER");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BusinessAccessService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public CommunityAccessScope currentCommunityScope() {
        if (hasAnyRole(GLOBAL_READ_ROLES)) {
            return CommunityAccessScope.globalScope();
        }
        if (hasRole("COMMUNITY_MANAGER")) {
            return CommunityAccessScope.restricted(readAuthorizedCommunityIds());
        }
        throwDenied();
        throw new IllegalStateException("unreachable");
    }

    public void assertCanCreateCommunity() {
        assertGlobalManager();
    }

    public void assertCanDeleteCommunity(UUID communityId) {
        if (hasAnyRole(GLOBAL_MANAGE_ROLES)) {
            return;
        }
        throwDenied();
    }

    public void assertCanReadCommunity(UUID communityId) {
        if (hasAnyRole(GLOBAL_READ_ROLES)) {
            return;
        }
        requireCommunity(communityId);
        if (hasRole("COMMUNITY_MANAGER")) {
            assertScopeAllows(
                    CommunityAccessScope.restricted(readAuthorizedCommunityIds()), communityId);
            return;
        }
        throwDenied();
    }

    public void assertCanManageCommunity(UUID communityId) {
        if (hasAnyRole(GLOBAL_MANAGE_ROLES)) {
            return;
        }
        requireCommunity(communityId);
        if (hasRole("COMMUNITY_MANAGER")) {
            assertScopeAllows(
                    CommunityAccessScope.restricted(readAuthorizedCommunityIds()), communityId);
            return;
        }
        throwDenied();
    }

    public void assertCanCreateBuilding(UUID communityId) {
        if (hasAnyRole(GLOBAL_MANAGE_ROLES)) {
            return;
        }
        requireCommunity(communityId);
        if (hasRole("COMMUNITY_MANAGER")) {
            assertScopeAllows(
                    CommunityAccessScope.restricted(readAuthorizedCommunityIds()), communityId);
            return;
        }
        throwDenied();
    }

    public void assertCanReadBuilding(UUID buildingId) {
        if (hasAnyRole(GLOBAL_READ_ROLES)) {
            return;
        }
        UUID communityId = buildingCommunityId(buildingId);
        if (hasRole("COMMUNITY_MANAGER")) {
            assertScopeAllows(
                    CommunityAccessScope.restricted(readAuthorizedCommunityIds()), communityId);
            return;
        }
        throwDenied();
    }

    public void assertCanManageBuilding(UUID buildingId) {
        if (hasAnyRole(GLOBAL_MANAGE_ROLES)) {
            return;
        }
        UUID communityId = buildingCommunityId(buildingId);
        if (hasRole("COMMUNITY_MANAGER")) {
            assertScopeAllows(
                    CommunityAccessScope.restricted(readAuthorizedCommunityIds()), communityId);
            return;
        }
        throwDenied();
    }

    /** 同时检查源楼栋和更新后的目标小区，阻止跨辖区迁移。 */
    public void assertCanMoveBuilding(UUID buildingId, UUID targetCommunityId) {
        if (hasAnyRole(GLOBAL_MANAGE_ROLES)) {
            return;
        }
        assertCanManageBuilding(buildingId);
        assertCanCreateBuilding(targetCommunityId);
    }

    /** 供评分、报告等已有模块复用社区管理员的资料范围。 */
    public boolean canAccessCommunity(UUID communityId) {
        if (hasAnyRole(GLOBAL_READ_ROLES)) {
            return true;
        }
        return hasRole("COMMUNITY_MANAGER") && readAuthorizedCommunityIds().contains(communityId);
    }

    private void assertGlobalManager() {
        if (!hasAnyRole(GLOBAL_MANAGE_ROLES)) {
            throwDenied();
        }
    }

    private void assertScopeAllows(CommunityAccessScope scope, UUID communityId) {
        if (!scope.allows(communityId)) {
            throwDenied();
        }
    }

    private UUID requireCommunity(UUID communityId) {
        if (communityId == null) {
            throw new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "小区不存在");
        }
        try {
            return jdbc.queryForObject("""
                    SELECT id FROM core.community
                    WHERE id=:communityId AND deleted_at IS NULL
                    """, Map.of("communityId", communityId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "小区不存在");
        }
    }

    private UUID buildingCommunityId(UUID buildingId) {
        try {
            return jdbc.queryForObject("""
                    SELECT community_id FROM core.building
                    WHERE id=:buildingId AND deleted_at IS NULL
                    """, Map.of("buildingId", buildingId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在");
        }
    }

    private Set<UUID> readAuthorizedCommunityIds() {
        Authentication authentication = authentication();
        UUID userId = CurrentUser.getUserId();
        MapSqlParameterSource params = new MapSqlParameterSource();
        String predicate;
        if (userId != null) {
            predicate = "id=:userId";
            params.addValue("userId", userId);
        } else {
            String username = authentication == null ? null : authentication.getName();
            if (username == null || username.isBlank()) {
                return Set.of();
            }
            predicate = "username=:username";
            params.addValue("username", username);
        }

        String profileJson;
        try {
            profileJson = jdbc.queryForObject("""
                    SELECT profile::text
                    FROM core.user_account
                    WHERE deleted_at IS NULL AND status='ACTIVE' AND %s
                    """.formatted(predicate), params, String.class);
        } catch (EmptyResultDataAccessException ex) {
            return Set.of();
        }
        if (profileJson == null || profileJson.isBlank()) {
            return Set.of();
        }

        try {
            JsonNode profile = objectMapper.readTree(profileJson);
            LinkedHashSet<UUID> ids = new LinkedHashSet<>();
            addUuid(ids, profile.path("communityId"));
            addUuidArray(ids, profile.path("communityIds"));
            addUuidArray(ids, profile.path("authorizedCommunityIds"));
            return Set.copyOf(ids);
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private void addUuidArray(Set<UUID> target, JsonNode node) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            addUuid(target, item);
        }
    }

    private void addUuid(Set<UUID> target, JsonNode node) {
        if (!node.isTextual()) {
            return;
        }
        try {
            target.add(UUID.fromString(node.textValue()));
        } catch (IllegalArgumentException ignored) {
            // 非法资料值不授予权限，也不让整个服务失败。
        }
    }

    private boolean hasAnyRole(Set<String> roles) {
        return roles.stream().anyMatch(this::hasRole);
    }

    private boolean hasRole(String role) {
        Authentication authentication = authentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + role));
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void throwDenied() {
        throw new AccessDeniedException("BUSINESS_ARCHIVE_ACCESS_DENIED");
    }
}
