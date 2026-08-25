package com.xiaou.aecp.identity;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMigrationTest {

    @Test
    void migrationCreatesSchemaAndDeterministicDemoSeed() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:f03_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("select count(*) from aecp_organization", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from aecp_user_account", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "select count(*) from aecp_organization_member where active = true",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from aecp_organization_member where user_id = 'USR-DEMO-ENG-A'",
                Integer.class)).isZero();
    }
}
