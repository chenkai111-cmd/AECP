package com.xiaou.aecp.identity.organization;

import java.time.Instant;

public record OrganizationMember(
        String organizationId,
        String userId,
        String username,
        String displayName,
        OrganizationRole role,
        Instant joinedAt) {
}
