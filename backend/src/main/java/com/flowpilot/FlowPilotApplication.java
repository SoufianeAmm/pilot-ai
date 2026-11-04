package com.flowpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * FlowPilot AI modular monolith. Every direct sub-package is an application module;
 * "shared" holds cross-cutting infrastructure that every module may use.
 */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class FlowPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowPilotApplication.class, args);
    }
}
