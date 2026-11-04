package com.flowpilot.support;

import java.nio.file.Path;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for integration tests that need a real database.
 *
 * <p>Starts one PostgreSQL + pgvector container per test JVM (shared by all subclasses) and
 * initialises it with the same script as Docker Compose, so tests run with the production
 * role layout: Flyway as {@code flowpilot_migrator}, the application as the DML-only
 * {@code flowpilot_app}. Permission mistakes therefore fail in tests, not in production.
 */
public abstract class PostgresIntegrationTest {

    protected static final String APP_USER = "flowpilot_app";
    protected static final String APP_PASSWORD = "app-test-password";
    protected static final String MIGRATOR_USER = "flowpilot_migrator";
    protected static final String MIGRATOR_PASSWORD = "migrator-test-password";

    private static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE)
            .withDatabaseName("flowpilot")
            .withEnv("FLOWPILOT_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("FLOWPILOT_DB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../infra/postgres/init/01-init.sh"), 0755),
                    "/docker-entrypoint-initdb.d/01-init.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.user", () -> MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);
    }
}
