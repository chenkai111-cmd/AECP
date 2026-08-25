package com.xiaou.web.organization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaou.aecp.identity.organization.OrganizationMember;
import com.xiaou.aecp.identity.organization.OrganizationRole;

import java.time.Instant;

public record OrganizationMemberResponse(
        @JsonProperty("user_id") String userId,
        String username,
        @JsonProperty("display_name") String displayName,
        OrganizationRole role,
        @JsonProperty("joined_at") Instant joinedAt) {

    static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.userId(),
                member.username(),
                member.displayName(),
                member.role(),
                member.joinedAt());
    }
}
