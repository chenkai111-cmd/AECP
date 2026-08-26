package com.xiaou.aecp.identity.organization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.ALREADY_ACTIVE;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.FORBIDDEN;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.LAST_ADMINISTRATOR;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.MEMBER_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.ORGANIZATION_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.UNAUTHENTICATED;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.USER_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ORGANIZATION_ADMIN;

@Service
public class OrganizationMemberService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationMemberService.class);

    private final OrganizationMemberRepository repository;
    private final Clock clock;

    public OrganizationMemberService(OrganizationMemberRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(String actorUsername, String organizationId) {
        requireOrganization(organizationId);
        requireAdministrator(actorUsername, organizationId);
        return repository.findActiveMembers(organizationId);
    }

    @Transactional(readOnly = true)
    public List<OrganizationUserCandidate> searchMemberCandidates(
            String actorUsername, String organizationId, String employeeNo) {
        requireOrganization(organizationId);
        requireAdministrator(actorUsername, organizationId);
        return repository.findMemberCandidates(organizationId, employeeNo == null ? "" : employeeNo.trim());
    }
    @Transactional
    public OrganizationMember addMember(
            String actorUsername, String organizationId, String userId, OrganizationRole role) {
        lockOrganization(organizationId);
        requireAdministrator(actorUsername, organizationId);
        repository.findEnabledUserById(userId).orElseThrow(() -> error(USER_NOT_FOUND));

        Optional<OrganizationMembership> existing = repository.findMembership(organizationId, userId);
        if (existing.filter(OrganizationMembership::active).isPresent()) {
            throw error(ALREADY_ACTIVE);
        }

        Instant now = clock.instant();
        try {
            if (existing.isPresent()) {
                repository.reactivateMembership(organizationId, userId, role, now);
            } else {
                repository.insertMembership(organizationId, userId, role, now);
            }
        } catch (DuplicateKeyException exception) {
            throw error(ALREADY_ACTIVE);
        }

        OrganizationMember result = requireActiveMember(organizationId, userId);
        logSuccess("add", organizationId, userId, actorUsername);
        return result;
    }

    @Transactional
    public OrganizationMember changeRole(
            String actorUsername, String organizationId, String userId, OrganizationRole role) {
        lockOrganization(organizationId);
        requireAdministrator(actorUsername, organizationId);
        OrganizationMembership membership = requireActiveMembership(organizationId, userId);

        if (membership.role() == role) {
            OrganizationMember result = requireActiveMember(organizationId, userId);
            logSuccess("change-role", organizationId, userId, actorUsername);
            return result;
        }
        protectLastAdministrator(organizationId, membership, role);

        repository.updateRole(organizationId, userId, role, clock.instant());
        OrganizationMember result = requireActiveMember(organizationId, userId);
        logSuccess("change-role", organizationId, userId, actorUsername);
        return result;
    }

    @Transactional
    public void removeMember(String actorUsername, String organizationId, String userId) {
        lockOrganization(organizationId);
        requireAdministrator(actorUsername, organizationId);
        OrganizationMembership membership = requireActiveMembership(organizationId, userId);

        if (membership.role() == ORGANIZATION_ADMIN
                && repository.countActiveAdministrators(organizationId) <= 1) {
            throw error(LAST_ADMINISTRATOR);
        }

        repository.deactivateMembership(organizationId, userId, clock.instant());
        logSuccess("remove", organizationId, userId, actorUsername);
    }

    private UserAccount requireAdministrator(String actorUsername, String organizationId) {
        UserAccount actor = repository.findEnabledUserByUsername(actorUsername)
                .orElseThrow(() -> error(UNAUTHENTICATED));
        OrganizationMembership membership = repository.findMembership(organizationId, actor.id())
                .filter(OrganizationMembership::active)
                .orElseThrow(() -> error(FORBIDDEN));
        if (membership.role() != ORGANIZATION_ADMIN) {
            throw error(FORBIDDEN);
        }
        return actor;
    }

    private void requireOrganization(String organizationId) {
        if (!repository.activeOrganizationExists(organizationId)) {
            throw error(ORGANIZATION_NOT_FOUND);
        }
    }

    private void lockOrganization(String organizationId) {
        if (!repository.lockActiveOrganization(organizationId)) {
            throw error(ORGANIZATION_NOT_FOUND);
        }
    }

    private OrganizationMembership requireActiveMembership(String organizationId, String userId) {
        return repository.findMembership(organizationId, userId)
                .filter(OrganizationMembership::active)
                .orElseThrow(() -> error(MEMBER_NOT_FOUND));
    }

    private OrganizationMember requireActiveMember(String organizationId, String userId) {
        return repository.findActiveMember(organizationId, userId)
                .orElseThrow(() -> error(MEMBER_NOT_FOUND));
    }

    private void protectLastAdministrator(
            String organizationId, OrganizationMembership membership, OrganizationRole requestedRole) {
        if (membership.role() == ORGANIZATION_ADMIN
                && requestedRole != ORGANIZATION_ADMIN
                && repository.countActiveAdministrators(organizationId) <= 1) {
            throw error(LAST_ADMINISTRATOR);
        }
    }

    private void logSuccess(String operation, String organizationId, String targetUserId, String actorUsername) {
        log.info("operation={} organizationId={} targetUserId={} actorUsername={} result=success",
                operation, organizationId, targetUserId, actorUsername);
    }

    private static OrganizationMemberError error(OrganizationMemberError.Reason reason) {
        return new OrganizationMemberError(reason);
    }
}
