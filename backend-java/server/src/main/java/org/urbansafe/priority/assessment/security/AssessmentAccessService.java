package org.urbansafe.priority.assessment.security;

import java.util.Collection;
import java.util.Map;
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

/** 第四阶段评分接口的角色边界和楼栋数据范围校验。 */
@Service
public class AssessmentAccessService {

    public static final String FULL_READ_ROLES = "hasAnyRole('ADMIN','GOVERNMENT_MANAGER',"
            + "'COMMUNITY_MANAGER','EXPERT','PROFESSIONAL_REVIEWER')";
    public static final String SUMMARY_READ_ROLES = "hasAnyRole('ADMIN','GOVERNMENT_MANAGER',"
            + "'COMMUNITY_MANAGER','EXPERT','PROFESSIONAL_REVIEWER','PROPERTY_INSPECTOR',"
            + "'DISPOSAL_OPERATOR')";
    public static final String CALCULATE_ROLES = "hasAnyRole('ADMIN','GOVERNMENT_MANAGER','COMMUNITY_MANAGER')";
    public static final String BATCH_AND_RANKING_ROLES = "hasAnyRole('ADMIN','GOVERNMENT_MANAGER')";
    public static final String ADMIN_ONLY = "hasRole('ADMIN')";

    private final NamedParameterJdbcTemplate jdbc;

    public AssessmentAccessService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertCanReadFull(UUID buildingId) {
        UUID communityId = buildingCommunityId(buildingId);
        if (hasAnyRole("ADMIN", "GOVERNMENT_MANAGER", "EXPERT", "PROFESSIONAL_REVIEWER")
                || communityManagerCanAccess(communityId)) {
            return;
        }
        throwDenied();
    }

    public void assertCanCalculate(UUID buildingId) {
        UUID communityId = buildingCommunityId(buildingId);
        if (hasAnyRole("ADMIN", "GOVERNMENT_MANAGER") || communityManagerCanAccess(communityId)) {
            return;
        }
        throwDenied();
    }

    public void assertCanReadSummary(UUID buildingId) {
        UUID communityId = buildingCommunityId(buildingId);
        if (hasAnyRole("ADMIN", "GOVERNMENT_MANAGER", "EXPERT", "PROFESSIONAL_REVIEWER",
                "PROPERTY_INSPECTOR", "DISPOSAL_OPERATOR") || communityManagerCanAccess(communityId)) {
            return;
        }
        throwDenied();
    }

    private boolean communityManagerCanAccess(UUID communityId) {
        return hasRole("COMMUNITY_MANAGER") && userAuthorizedForCommunity(communityId);
    }

    private UUID buildingCommunityId(UUID buildingId) {
        try {
            return jdbc.queryForObject("""
                    SELECT community_id
                    FROM core.building
                    WHERE id=:buildingId AND deleted_at IS NULL
                    """, Map.of("buildingId", buildingId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在");
        }
    }

    private boolean userAuthorizedForCommunity(UUID communityId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "" : authentication.getName();
        UUID userId = CurrentUser.getUserId();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("communityIdText", communityId.toString());
        String identityPredicate;
        if (userId != null) {
            identityPredicate = "u.id=:userId";
            params.addValue("userId", userId);
        } else {
            if (username == null || username.isBlank()) {
                return false;
            }
            identityPredicate = "u.username=:username";
            params.addValue("username", username);
        }
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM core.user_account u
                    WHERE u.deleted_at IS NULL AND u.status='ACTIVE'
                      AND %s
                      AND (
                            u.profile->>'communityId'=:communityIdText
                         OR jsonb_exists(COALESCE(u.profile->'communityIds', '[]'::jsonb), :communityIdText)
                         OR jsonb_exists(COALESCE(u.profile->'authorizedCommunityIds', '[]'::jsonb), :communityIdText)
                      )
                )
                """.formatted(identityPredicate), params, Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }

    private boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + role));
    }

    private void throwDenied() {
        throw new AccessDeniedException("ASSESSMENT_ACCESS_DENIED");
    }
}
