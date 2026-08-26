package com.xiaou.web.organization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaou.aecp.identity.organization.OrganizationUserCandidate;

public record OrganizationUserCandidateResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("employee_no") String employeeNo,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("already_member") boolean alreadyMember) {

    static OrganizationUserCandidateResponse from(OrganizationUserCandidate candidate) {
        return new OrganizationUserCandidateResponse(
                candidate.userId(), candidate.employeeNo(), candidate.displayName(), candidate.alreadyMember());
    }
}