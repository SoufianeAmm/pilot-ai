package com.flowpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.flowpilot.support.PostgresIntegrationTest;

/**
 * Verifies the least-privilege database layout: the application role can use the schema
 * and pgvector, but cannot change the schema. Only the migrator can.
 */
@SpringBootTest
class DatabaseRolesIT extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void applicationConnectsAsDmlOnlyRole() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_USER);
    }

    @Test
    void flywayAppliedBaselineAsMigrator() {
        String installedBy = jdbc.queryForObject(
                "SELECT installed_by FROM app.flyway_schema_history WHERE version = '1' AND success", String.class);

        assertThat(installedBy).isEqualTo(MIGRATOR_USER);
    }

    @Test
    void applicationRoleCannotCreateTables() {
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE app.should_not_exist (id int)"))
                .rootCause()
                .hasMessageContaining("permission denied for schema app");
    }

    @Test
    void pgvectorIsAvailableToApplicationRole() {
        Double distance = jdbc.queryForObject("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector", Double.class);

        assertThat(distance).isEqualTo(1.0);
    }
}
