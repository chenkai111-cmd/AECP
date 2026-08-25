package com.xiaou.aecp.identity.organization;

public final class OrganizationMemberError extends RuntimeException {

    public enum Reason {
        UNAUTHENTICATED,
        FORBIDDEN,
        ORGANIZATION_NOT_FOUND,
        USER_NOT_FOUND,
        MEMBER_NOT_FOUND,
        ALREADY_ACTIVE,
        LAST_ADMINISTRATOR
    }

    private final Reason reason;

    public OrganizationMemberError(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
