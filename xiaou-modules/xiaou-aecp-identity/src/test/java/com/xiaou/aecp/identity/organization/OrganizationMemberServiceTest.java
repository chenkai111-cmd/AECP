package com.xiaou.aecp.identity.organization;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.ALREADY_ACTIVE;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.FORBIDDEN;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.LAST_ADMINISTRATOR;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.MEMBER_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.ORGANIZATION_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.UNAUTHENTICATED;
import static com.xiaou.aecp.identity.organization.OrganizationMemberError.Reason.USER_NOT_FOUND;
import static com.xiaou.aecp.identity.organization.OrganizationRole.AUDITOR;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ENGINEER;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ORGANIZATION_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String COMAC = "ORG-DEMO-COMAC";
    private static final String AECC = "ORG-DEMO-AECC";
    private static final UserAccount ADMIN_A =
            new UserAccount("USR-DEMO-ADMIN-A", "demo-admin-a", "演示管理员 A");
    private static final UserAccount ENGINEER_A =
            new UserAccount("USR-DEMO-ENG-A", "demo-engineer-a", "演示工程师 A");

    @Test
    void adminListsOnlyActiveMembersInRepositoryOrder() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);
        repository.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, true, NOW.minusSeconds(20));
        repository.putAccount(new UserAccount("USR-INACTIVE", "inactive", "Inactive"));
        repository.putMembership(COMAC, "USR-INACTIVE", AUDITOR, false, NOW.minusSeconds(10));

        List<OrganizationMember> result = service(repository).listMembers(ADMIN_A.username(), COMAC);

        assertThat(result).extracting(OrganizationMember::userId)
                .containsExactly(ADMIN_A.id(), ENGINEER_A.id());
    }

    @Test
    void missingActorAccountIsUnauthenticated() {
        assertReason(UNAUTHENTICATED,
                () -> service(standardRepository()).listMembers("missing", COMAC));
    }

    @Test
    void nonAdminAndCrossOrganizationActorAreForbidden() {
        FakeOrganizationMemberRepository nonAdmin = standardRepository();
        nonAdmin.putAccount(ENGINEER_A);
        nonAdmin.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, true, NOW);
        assertReason(FORBIDDEN,
                () -> service(nonAdmin).listMembers(ENGINEER_A.username(), COMAC));

        FakeOrganizationMemberRepository crossOrganization = standardRepository();
        assertReason(FORBIDDEN,
                () -> service(crossOrganization).listMembers(ADMIN_A.username(), AECC));
    }

    @Test
    void missingOrganizationIsNotFound() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.organizations.remove(COMAC);

        assertReason(ORGANIZATION_NOT_FOUND,
                () -> service(repository).listMembers(ADMIN_A.username(), COMAC));
        assertReason(ORGANIZATION_NOT_FOUND,
                () -> service(repository).addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), ENGINEER));
    }

    @Test
    void adminAddsEnabledUser() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);

        OrganizationMember result = service(repository)
                .addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), ENGINEER);

        assertThat(result.userId()).isEqualTo(ENGINEER_A.id());
        assertThat(result.role()).isEqualTo(ENGINEER);
        assertThat(result.joinedAt()).isEqualTo(NOW);
    }

    @Test
    void missingOrDisabledTargetUserIsNotFound() {
        FakeOrganizationMemberRepository repository = standardRepository();

        assertReason(USER_NOT_FOUND,
                () -> service(repository).addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), ENGINEER));
        assertThat(repository.writeCalls).isZero();
    }

    @Test
    void activeMemberCannotBeAddedTwice() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);
        repository.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, true, NOW);

        assertReason(ALREADY_ACTIVE,
                () -> service(repository).addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), AUDITOR));
    }

    @Test
    void inactiveMemberIsReactivatedWithNewRoleAndJoinTime() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);
        repository.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, false, NOW.minusSeconds(60));

        OrganizationMember result = service(repository)
                .addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), AUDITOR);

        assertThat(repository.lastRole).isEqualTo(AUDITOR);
        assertThat(repository.lastNow).isEqualTo(NOW);
        assertThat(result.role()).isEqualTo(AUDITOR);
        assertThat(result.joinedAt()).isEqualTo(NOW);
    }

    @Test
    void duplicateKeyRaceMapsToAlreadyActive() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);
        repository.failInsertWithDuplicate = true;

        assertReason(ALREADY_ACTIVE,
                () -> service(repository).addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), ENGINEER));
    }

    @Test
    void changingToSameRoleIsIdempotent() {
        FakeOrganizationMemberRepository repository = repositoryWithEngineer();

        OrganizationMember result = service(repository)
                .changeRole(ADMIN_A.username(), COMAC, ENGINEER_A.id(), ENGINEER);

        assertThat(result.role()).isEqualTo(ENGINEER);
        assertThat(repository.updateRoleCalls).isZero();
    }

    @Test
    void activeMemberRoleCanBeChanged() {
        FakeOrganizationMemberRepository repository = repositoryWithEngineer();

        OrganizationMember result = service(repository)
                .changeRole(ADMIN_A.username(), COMAC, ENGINEER_A.id(), AUDITOR);

        assertThat(repository.lastRole).isEqualTo(AUDITOR);
        assertThat(repository.lastNow).isEqualTo(NOW);
        assertThat(result.role()).isEqualTo(AUDITOR);
    }

    @Test
    void inactiveOrMissingMemberCannotBeChanged() {
        FakeOrganizationMemberRepository missing = standardRepository();
        assertReason(MEMBER_NOT_FOUND,
                () -> service(missing).changeRole(ADMIN_A.username(), COMAC, ENGINEER_A.id(), AUDITOR));

        FakeOrganizationMemberRepository inactive = repositoryWithEngineer();
        inactive.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, false, NOW);
        assertReason(MEMBER_NOT_FOUND,
                () -> service(inactive).changeRole(ADMIN_A.username(), COMAC, ENGINEER_A.id(), AUDITOR));
    }

    @Test
    void ordinaryMemberCanBeSoftRemoved() {
        FakeOrganizationMemberRepository repository = repositoryWithEngineer();

        service(repository).removeMember(ADMIN_A.username(), COMAC, ENGINEER_A.id());

        assertThat(repository.lastNow).isEqualTo(NOW);
        assertThat(repository.findMembership(COMAC, ENGINEER_A.id())).get()
                .extracting(OrganizationMembership::active).isEqualTo(false);
    }

    @Test
    void inactiveOrMissingMemberCannotBeRemoved() {
        FakeOrganizationMemberRepository missing = standardRepository();
        assertReason(MEMBER_NOT_FOUND,
                () -> service(missing).removeMember(ADMIN_A.username(), COMAC, ENGINEER_A.id()));

        FakeOrganizationMemberRepository inactive = repositoryWithEngineer();
        inactive.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, false, NOW);
        assertReason(MEMBER_NOT_FOUND,
                () -> service(inactive).removeMember(ADMIN_A.username(), COMAC, ENGINEER_A.id()));
    }

    @Test
    void lastAdministratorCannotBeDemotedOrRemoved() {
        FakeOrganizationMemberRepository demote = standardRepository();
        assertReason(LAST_ADMINISTRATOR,
                () -> service(demote).changeRole(ADMIN_A.username(), COMAC, ADMIN_A.id(), ENGINEER));
        assertThat(demote.updateRoleCalls).isZero();

        FakeOrganizationMemberRepository remove = standardRepository();
        assertReason(LAST_ADMINISTRATOR,
                () -> service(remove).removeMember(ADMIN_A.username(), COMAC, ADMIN_A.id()));
        assertThat(remove.deactivateCalls).isZero();
    }

    @Test
    void administratorCanBeDemotedOrRemovedWhenAnotherAdministratorExists() {
        UserAccount secondAdmin = new UserAccount("USR-ADMIN-2", "admin-2", "Admin 2");
        FakeOrganizationMemberRepository demote = standardRepository();
        demote.putAccount(secondAdmin);
        demote.putMembership(COMAC, secondAdmin.id(), ORGANIZATION_ADMIN, true, NOW);
        assertThat(service(demote).changeRole(ADMIN_A.username(), COMAC, ADMIN_A.id(), ENGINEER).role())
                .isEqualTo(ENGINEER);

        FakeOrganizationMemberRepository remove = standardRepository();
        remove.putAccount(secondAdmin);
        remove.putMembership(COMAC, secondAdmin.id(), ORGANIZATION_ADMIN, true, NOW);
        service(remove).removeMember(ADMIN_A.username(), COMAC, ADMIN_A.id());
        assertThat(remove.findMembership(COMAC, ADMIN_A.id())).get()
                .extracting(OrganizationMembership::active).isEqualTo(false);
    }

    @Test
    void writeLocksOrganizationBeforeAuthorizationAndMutation() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);

        service(repository).addMember(ADMIN_A.username(), COMAC, ENGINEER_A.id(), ENGINEER);

        assertThat(repository.calls).startsWith(
                "lock organization", "find actor", "find actor membership");
    }

    private static OrganizationMemberService service(FakeOrganizationMemberRepository repository) {
        return new OrganizationMemberService(repository, CLOCK);
    }

    private static FakeOrganizationMemberRepository standardRepository() {
        FakeOrganizationMemberRepository repository = new FakeOrganizationMemberRepository();
        repository.organizations.add(COMAC);
        repository.organizations.add(AECC);
        repository.putAccount(ADMIN_A);
        repository.putMembership(COMAC, ADMIN_A.id(), ORGANIZATION_ADMIN, true, NOW.minusSeconds(3600));
        return repository;
    }

    private static FakeOrganizationMemberRepository repositoryWithEngineer() {
        FakeOrganizationMemberRepository repository = standardRepository();
        repository.putAccount(ENGINEER_A);
        repository.putMembership(COMAC, ENGINEER_A.id(), ENGINEER, true, NOW.minusSeconds(30));
        return repository;
    }

    private static void assertReason(OrganizationMemberError.Reason reason, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(OrganizationMemberError.class,
                        error -> assertThat(error.reason()).isEqualTo(reason));
    }

    private static final class FakeOrganizationMemberRepository implements OrganizationMemberRepository {
        private final List<String> organizations = new ArrayList<>();
        private final Map<String, UserAccount> accountsById = new LinkedHashMap<>();
        private final Map<String, UserAccount> accountsByUsername = new LinkedHashMap<>();
        private final Map<String, OrganizationMembership> memberships = new LinkedHashMap<>();
        private final List<String> calls = new ArrayList<>();
        private int writeCalls;
        private int updateRoleCalls;
        private int deactivateCalls;
        private OrganizationRole lastRole;
        private Instant lastNow;
        private boolean failInsertWithDuplicate;

        void putAccount(UserAccount account) {
            accountsById.put(account.id(), account);
            accountsByUsername.put(account.username(), account);
        }

        void putMembership(
                String organizationId, String userId, OrganizationRole role, boolean active, Instant joinedAt) {
            memberships.put(key(organizationId, userId),
                    new OrganizationMembership(organizationId, userId, role, active, joinedAt));
        }

        @Override
        public boolean activeOrganizationExists(String organizationId) {
            return organizations.contains(organizationId);
        }

        @Override
        public boolean lockActiveOrganization(String organizationId) {
            calls.add("lock organization");
            return organizations.contains(organizationId);
        }

        @Override
        public Optional<UserAccount> findEnabledUserByUsername(String username) {
            calls.add("find actor");
            return Optional.ofNullable(accountsByUsername.get(username));
        }

        @Override
        public Optional<UserAccount> findEnabledUserById(String userId) {
            return Optional.ofNullable(accountsById.get(userId));
        }

        @Override
        public Optional<OrganizationMembership> findMembership(String organizationId, String userId) {
            if (userId.equals(ADMIN_A.id())) {
                calls.add("find actor membership");
            }
            return Optional.ofNullable(memberships.get(key(organizationId, userId)));
        }

        @Override
        public Optional<OrganizationMember> findActiveMember(String organizationId, String userId) {
            return findMembership(organizationId, userId)
                    .filter(OrganizationMembership::active)
                    .flatMap(membership -> Optional.ofNullable(accountsById.get(userId))
                            .map(account -> view(membership, account)));
        }

        @Override
        public List<OrganizationMember> findActiveMembers(String organizationId) {
            return memberships.values().stream()
                    .filter(membership -> membership.organizationId().equals(organizationId))
                    .filter(OrganizationMembership::active)
                    .map(membership -> view(membership, accountsById.get(membership.userId())))
                    .toList();
        }

        @Override
        public long countActiveAdministrators(String organizationId) {
            return memberships.values().stream()
                    .filter(membership -> membership.organizationId().equals(organizationId))
                    .filter(OrganizationMembership::active)
                    .filter(membership -> membership.role() == ORGANIZATION_ADMIN)
                    .count();
        }

        @Override
        public void insertMembership(String organizationId, String userId, OrganizationRole role, Instant now) {
            writeCalls++;
            if (failInsertWithDuplicate) {
                throw new DuplicateKeyException("duplicate");
            }
            lastRole = role;
            lastNow = now;
            putMembership(organizationId, userId, role, true, now);
        }

        @Override
        public void reactivateMembership(String organizationId, String userId, OrganizationRole role, Instant now) {
            writeCalls++;
            lastRole = role;
            lastNow = now;
            putMembership(organizationId, userId, role, true, now);
        }

        @Override
        public void updateRole(String organizationId, String userId, OrganizationRole role, Instant now) {
            writeCalls++;
            updateRoleCalls++;
            lastRole = role;
            lastNow = now;
            OrganizationMembership current = memberships.get(key(organizationId, userId));
            putMembership(organizationId, userId, role, true, current.joinedAt());
        }

        @Override
        public void deactivateMembership(String organizationId, String userId, Instant now) {
            writeCalls++;
            deactivateCalls++;
            lastNow = now;
            OrganizationMembership current = memberships.get(key(organizationId, userId));
            putMembership(organizationId, userId, current.role(), false, current.joinedAt());
        }

        private static OrganizationMember view(OrganizationMembership membership, UserAccount account) {
            return new OrganizationMember(
                    membership.organizationId(), membership.userId(), account.username(), account.displayName(),
                    membership.role(), membership.joinedAt());
        }

        private static String key(String organizationId, String userId) {
            return organizationId + ":" + userId;
        }
    }
}
