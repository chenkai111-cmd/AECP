package com.xiaou.aecp.identity.organization;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcOrganizationMemberRepository implements OrganizationMemberRepository {

    private static final String ACTIVE_ORGANIZATION_SQL = """
            SELECT id FROM aecp_organization
            WHERE id = :organizationId AND active = TRUE
            """;
    private static final String LOCK_ACTIVE_ORGANIZATION_SQL = """
            SELECT id FROM aecp_organization
            WHERE id = :organizationId AND active = TRUE
            FOR UPDATE
            """;
    private static final String USER_BY_USERNAME_SQL = """
            SELECT id, username, display_name FROM aecp_user_account
            WHERE username = :username AND enabled = TRUE
            """;
    private static final String USER_BY_ID_SQL = """
            SELECT id, username, display_name FROM aecp_user_account
            WHERE id = :userId AND enabled = TRUE
            """;
    private static final String MEMBERSHIP_SQL = """
            SELECT organization_id, user_id, role, active, joined_at
            FROM aecp_organization_member
            WHERE organization_id = :organizationId AND user_id = :userId
            """;
    private static final String ACTIVE_MEMBER_SQL = """
            SELECT m.organization_id, m.user_id, u.username, u.display_name, m.role, m.joined_at
            FROM aecp_organization_member m
            JOIN aecp_user_account u ON u.id = m.user_id
            WHERE m.organization_id = :organizationId
              AND m.user_id = :userId
              AND m.active = TRUE
              AND u.enabled = TRUE
            """;
    private static final String ACTIVE_MEMBERS_SQL = """
            SELECT m.organization_id, m.user_id, u.username, u.display_name, m.role, m.joined_at
            FROM aecp_organization_member m
            JOIN aecp_user_account u ON u.id = m.user_id
            WHERE m.organization_id = :organizationId
              AND m.active = TRUE
              AND u.enabled = TRUE
            ORDER BY m.joined_at ASC, m.user_id ASC
            """;
    private static final String ADMINISTRATOR_COUNT_SQL = """
            SELECT COUNT(*) FROM aecp_organization_member
            WHERE organization_id = :organizationId
              AND active = TRUE
              AND role = 'ORGANIZATION_ADMIN'
            """;
    private static final String INSERT_MEMBERSHIP_SQL = """
            INSERT INTO aecp_organization_member
                (organization_id, user_id, role, active, joined_at, updated_at, removed_at)
            VALUES
                (:organizationId, :userId, :role, TRUE, :now, :now, NULL)
            """;
    private static final String REACTIVATE_MEMBERSHIP_SQL = """
            UPDATE aecp_organization_member
            SET role = :role, active = TRUE, joined_at = :now, updated_at = :now, removed_at = NULL
            WHERE organization_id = :organizationId AND user_id = :userId
            """;
    private static final String UPDATE_ROLE_SQL = """
            UPDATE aecp_organization_member
            SET role = :role, updated_at = :now
            WHERE organization_id = :organizationId AND user_id = :userId AND active = TRUE
            """;
    private static final String DEACTIVATE_MEMBERSHIP_SQL = """
            UPDATE aecp_organization_member
            SET active = FALSE, updated_at = :now, removed_at = :now
            WHERE organization_id = :organizationId AND user_id = :userId AND active = TRUE
            """;

    private static final RowMapper<UserAccount> USER_MAPPER = (resultSet, rowNumber) -> new UserAccount(
            resultSet.getString("id"),
            resultSet.getString("username"),
            resultSet.getString("display_name"));
    private static final RowMapper<OrganizationMembership> MEMBERSHIP_MAPPER = (resultSet, rowNumber) ->
            new OrganizationMembership(
                    resultSet.getString("organization_id"),
                    resultSet.getString("user_id"),
                    OrganizationRole.valueOf(resultSet.getString("role")),
                    resultSet.getBoolean("active"),
                    resultSet.getTimestamp("joined_at").toInstant());
    private static final RowMapper<OrganizationMember> MEMBER_MAPPER = (resultSet, rowNumber) ->
            new OrganizationMember(
                    resultSet.getString("organization_id"),
                    resultSet.getString("user_id"),
                    resultSet.getString("username"),
                    resultSet.getString("display_name"),
                    OrganizationRole.valueOf(resultSet.getString("role")),
                    resultSet.getTimestamp("joined_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOrganizationMemberRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean activeOrganizationExists(String organizationId) {
        return !jdbc.query(ACTIVE_ORGANIZATION_SQL, organizationParameters(organizationId),
                (resultSet, rowNumber) -> resultSet.getString("id")).isEmpty();
    }

    @Override
    public boolean lockActiveOrganization(String organizationId) {
        return !jdbc.query(LOCK_ACTIVE_ORGANIZATION_SQL, organizationParameters(organizationId),
                (resultSet, rowNumber) -> resultSet.getString("id")).isEmpty();
    }

    @Override
    public Optional<UserAccount> findEnabledUserByUsername(String username) {
        return first(jdbc.query(USER_BY_USERNAME_SQL, Map.of("username", username), USER_MAPPER));
    }

    @Override
    public Optional<UserAccount> findEnabledUserById(String userId) {
        return first(jdbc.query(USER_BY_ID_SQL, Map.of("userId", userId), USER_MAPPER));
    }

    @Override
    public Optional<OrganizationMembership> findMembership(String organizationId, String userId) {
        return first(jdbc.query(MEMBERSHIP_SQL, memberParameters(organizationId, userId), MEMBERSHIP_MAPPER));
    }

    @Override
    public Optional<OrganizationMember> findActiveMember(String organizationId, String userId) {
        return first(jdbc.query(ACTIVE_MEMBER_SQL, memberParameters(organizationId, userId), MEMBER_MAPPER));
    }

    @Override
    public List<OrganizationMember> findActiveMembers(String organizationId) {
        return jdbc.query(ACTIVE_MEMBERS_SQL, organizationParameters(organizationId), MEMBER_MAPPER);
    }

    @Override
    public long countActiveAdministrators(String organizationId) {
        Long count = jdbc.queryForObject(
                ADMINISTRATOR_COUNT_SQL, organizationParameters(organizationId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void insertMembership(String organizationId, String userId, OrganizationRole role, Instant now) {
        jdbc.update(INSERT_MEMBERSHIP_SQL, writeParameters(organizationId, userId, role, now));
    }

    @Override
    public void reactivateMembership(String organizationId, String userId, OrganizationRole role, Instant now) {
        jdbc.update(REACTIVATE_MEMBERSHIP_SQL, writeParameters(organizationId, userId, role, now));
    }

    @Override
    public void updateRole(String organizationId, String userId, OrganizationRole role, Instant now) {
        jdbc.update(UPDATE_ROLE_SQL, writeParameters(organizationId, userId, role, now));
    }

    @Override
    public void deactivateMembership(String organizationId, String userId, Instant now) {
        jdbc.update(DEACTIVATE_MEMBERSHIP_SQL,
                memberParameters(organizationId, userId).addValue("now", Timestamp.from(now)));
    }

    private static Map<String, String> organizationParameters(String organizationId) {
        return Map.of("organizationId", organizationId);
    }

    private static MapSqlParameterSource memberParameters(String organizationId, String userId) {
        return new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("userId", userId);
    }

    private static MapSqlParameterSource writeParameters(
            String organizationId, String userId, OrganizationRole role, Instant now) {
        return memberParameters(organizationId, userId)
                .addValue("role", role.name())
                .addValue("now", Timestamp.from(now));
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
