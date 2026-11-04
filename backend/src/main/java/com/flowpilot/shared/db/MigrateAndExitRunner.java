package com.flowpilot.shared.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In the "migrate" profile the application starts without a web server, lets Flyway apply
 * pending migrations during startup, then exits with code 0. Docker Compose runs this once
 * before the replicas start, so replicas never race each other to migrate the schema.
 * A failed migration aborts startup, which exits with a non-zero code.
 */
@Component
@Profile("migrate")
class MigrateAndExitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrateAndExitRunner.class);

    private final ConfigurableApplicationContext context;

    MigrateAndExitRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Database migrations are up to date; exiting migrate mode");
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
