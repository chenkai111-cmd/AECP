package com.xiaou.web.organization;

import com.xiaou.aecp.identity.organization.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationMemberRoleRequest(@NotNull OrganizationRole role) {
}
