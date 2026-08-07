package org.urbansafe.priority.common.security;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 当前用户可访问的小区范围。 */
public record CommunityAccessScope(boolean global, Set<UUID> communityIds) {

    public CommunityAccessScope {
        communityIds = Set.copyOf(communityIds == null ? Set.of() : communityIds);
    }

    public static CommunityAccessScope globalScope() {
        return new CommunityAccessScope(true, Set.of());
    }

    public static CommunityAccessScope restricted(Set<UUID> communityIds) {
        return new CommunityAccessScope(false, new LinkedHashSet<>(communityIds));
    }

    public boolean allows(UUID communityId) {
        return global || (communityId != null && communityIds.contains(communityId));
    }
}
