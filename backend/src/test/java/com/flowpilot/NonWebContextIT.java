package com.flowpilot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import com.flowpilot.support.PostgresIntegrationTest;

/**
 * The "migrate" Docker service starts the application without a web server. This guards against
 * beans that only work in a web context (e.g. the security filter chain) breaking that mode.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class NonWebContextIT extends PostgresIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void applicationStartsWithoutAWebServer_andSkipsWebSecurity() {
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBean("flywayInitializer")).isNotNull();
    }
}
