package com.xiaou.aecp.identity.organization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository {

    boolean activeOrganizationExists(String organizationId);

    boolean lockActiveOrganization(String organizationId);

    Optional<UserAccount> findEnabledUserByUsername(String username);

    Optional<UserAccount> findEnabledUserById(String userId);

    Optional<OrganizationMembership> findMembership(String organizationId, String userId);

    Optional<OrganizationMember> findActiveMember(String organizationId, String userId);

    List<OrganizationMember> findActiveMembers(String organizationId);

    List<OrganizationUserCandidate> findMemberCandidates(String organizationId, String employeeNo);

    long countActiveAdministrators(String organizationId);

    void insertMembership(String organizationId, String userId, OrganizationRole role, Instant now);

    void reactivateMembership(String organizationId, String userId, OrganizationRole role, Instant now);

    void updateRole(String organizationId, String userId, OrganizationRole role, Instant now);

    void deactivateMembership(String organizationId, String userId, Instant now);
}
