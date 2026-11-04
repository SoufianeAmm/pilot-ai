package com.flowpilot;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build if a module uses another module's internal types or if module
 * dependencies form a cycle. This is what keeps the monolith "modular".
 */
class ModularityTests {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(FlowPilotApplication.class).verify();
    }
}
