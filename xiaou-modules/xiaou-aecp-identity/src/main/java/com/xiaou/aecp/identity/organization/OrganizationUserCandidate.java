package com.xiaou.aecp.identity.organization;

public record OrganizationUserCandidate(
        String userId,
        String employeeNo,
        String displayName,
        boolean alreadyMember) {
}