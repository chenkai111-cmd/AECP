package com.xiaou.web.organization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaou.aecp.identity.organization.OrganizationRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddOrganizationMemberRequest(
        @NotBlank @JsonProperty("user_id") String userId,
        @NotNull OrganizationRole role) {
}
