package com.xiaou.web.organization;

import java.util.List;

public record OrganizationMemberListResponse(
        List<OrganizationMemberResponse> items,
        long total) {
}
