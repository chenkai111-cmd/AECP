package com.xiaou.aecp.identity.organization;

import java.time.Instant;

public record OrganizationMembership(
        String organizationId,
        String userId,
        OrganizationRole role,
        boolean active,
        Instant joinedAt) {
}
