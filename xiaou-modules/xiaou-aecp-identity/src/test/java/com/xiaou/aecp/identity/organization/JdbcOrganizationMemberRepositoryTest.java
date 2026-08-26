package com.xiaou.aecp.identity.organization;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.xiaou.aecp.identity.organization.OrganizationRole.AUDITOR;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ENGINEER;
import static com.xiaou.aecp.identity.organization.OrganizationRole.ORGANIZATION_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOrganizationMemberRepositoryTest {

    private static final String COMAC = "ORG-DEMO-COMAC";
    private static final String ENG_A = "USR-DEMO-ENG-A";
    private static final Instant FIRST = Instant.parse("2026-08-25T02:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-08-25T03:00:00Z");

    private JdbcOrganizationMemberRepository repository;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/dev-migration")
                .load()
                .migrate();
        repository = new JdbcOrganizationMemberRepository(new NamedParameterJdbcTemplate(dataSource));
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void findsAndLocksOnlyActiveOrganization() {
        assertThat(repository.activeOrganizationExists(COMAC)).isTrue();
        assertThat(repository.activeOrganizationExists("UNKNOWN")).isFalse();

        assertThat(Boolean.TRUE.equals(
                transactions.execute(status -> repository.lockActiveOrganization(COMAC)))).isTrue();
        assertThat(Boolean.TRUE.equals(
                transactions.execute(status -> repository.lockActiveOrganization("UNKNOWN")))).isFalse();
    }

    @Test
    void findsOnlyEnabledUsersByUsernameAndId() {
        assertThat(repository.findEnabledUserByUsername("demo-admin-a")).get()
                .isEqualTo(new UserAccount("USR-DEMO-ADMIN-A", "demo-admin-a", "演示管理员 A"));
        assertThat(repository.findEnabledUserById(ENG_A)).get()
                .isEqualTo(new UserAccount(ENG_A, "demo-engineer-a", "演示工程师 A"));
        assertThat(repository.findEnabledUserByUsername("missing")).isEmpty();

        jdbc.update("UPDATE aecp_user_account SET enabled = FALSE WHERE id = ?", ENG_A);
        assertThat(repository.findEnabledUserById(ENG_A)).isEmpty();
    }

    @Test
    void insertsAndReadsActiveMemberView() {
        repository.insertMembership(COMAC, ENG_A, ENGINEER, FIRST);

        assertThat(repository.findActiveMember(COMAC, ENG_A)).get()
                .isEqualTo(new OrganizationMember(
                        COMAC, ENG_A, "demo-engineer-a", "演示工程师 A", ENGINEER, FIRST));
    }

    @Test
    void duplicateInsertRaisesDuplicateKeyException() {
        repository.insertMembership(COMAC, ENG_A, ENGINEER, FIRST);

        assertThatThrownBy(() -> repository.insertMembership(COMAC, ENG_A, AUDITOR, SECOND))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void reactivatesRemovedMemberWithNewRoleAndTime() {
        repository.insertMembership(COMAC, ENG_A, ENGINEER, FIRST);
        repository.deactivateMembership(COMAC, ENG_A, FIRST.plusSeconds(60));

        repository.reactivateMembership(COMAC, ENG_A, AUDITOR, SECOND);

        assertThat(repository.findMembership(COMAC, ENG_A)).get()
                .isEqualTo(new OrganizationMembership(COMAC, ENG_A, AUDITOR, true, SECOND));
        assertThat(jdbc.queryForObject(
                "SELECT removed_at FROM aecp_organization_member WHERE organization_id = ? AND user_id = ?",
                Timestamp.class, COMAC, ENG_A)).isNull();
    }

    @Test
    void listsOnlyActiveMembersInStableOrder() {
        repository.insertMembership(COMAC, "USR-DEMO-ENG-B", ENGINEER, FIRST);
        repository.insertMembership(COMAC, ENG_A, ENGINEER, FIRST);
        repository.insertMembership(COMAC, "USR-DEMO-AUDITOR", AUDITOR, FIRST);
        repository.deactivateMembership(COMAC, "USR-DEMO-AUDITOR", SECOND);
        repository.deactivateMembership(COMAC, "USR-DEMO-ADMIN-A", SECOND);

        List<OrganizationMember> result = repository.findActiveMembers(COMAC);

        assertThat(result).extracting(OrganizationMember::userId)
                .containsExactly(ENG_A, "USR-DEMO-ENG-B");
    }

    @Test
    void updatesRoleAndUpdatedAt() {
        repository.insertMembership(COMAC, ENG_A, ENGINEER, FIRST);

        repository.updateRole(COMAC, ENG_A, AUDITOR, SECOND);

        assertThat(repository.findActiveMember(COMAC, ENG_A)).get()
                .extracting(OrganizationMember::role).isEqualTo(AUDITOR);
        assertThat(jdbc.queryForObject(
                "SELECT updated_at FROM aecp_organization_member WHERE organization_id = ? AND user_id = ?",
                Timestamp.class, COMAC, ENG_A).toInstant()).isEqualTo(SECOND);
    }

    @Test
    void deactivatesWithoutDeletingRow() {
        repository.insertMembership(COMAC, ENG_A, ENGINEER, FIRST);

        repository.deactivateMembership(COMAC, ENG_A, SECOND);

        assertThat(repository.findActiveMember(COMAC, ENG_A)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM aecp_organization_member WHERE organization_id = ? AND user_id = ?",
                Integer.class, COMAC, ENG_A)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT removed_at FROM aecp_organization_member WHERE organization_id = ? AND user_id = ?",
                Timestamp.class, COMAC, ENG_A).toInstant()).isEqualTo(SECOND);
    }

    @Test
    void countsOnlyActiveOrganizationAdministrators() {
        assertThat(repository.countActiveAdministrators(COMAC)).isEqualTo(1);

        repository.insertMembership(COMAC, ENG_A, ORGANIZATION_ADMIN, FIRST);
        assertThat(repository.countActiveAdministrators(COMAC)).isEqualTo(2);

        jdbc.update("UPDATE aecp_user_account SET enabled = FALSE WHERE id = ?", ENG_A);
        assertThat(repository.countActiveAdministrators(COMAC)).isEqualTo(1);

        jdbc.update("UPDATE aecp_user_account SET enabled = TRUE WHERE id = ?", ENG_A);

        repository.deactivateMembership(COMAC, ENG_A, SECOND);
        assertThat(repository.countActiveAdministrators(COMAC)).isEqualTo(1);
    }
}
