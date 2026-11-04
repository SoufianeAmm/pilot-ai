package com.flowpilot.shared.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, non-sensitive application metadata. "instance" identifies the replica that
 * served the request (the container hostname), which the load-balancing tests rely on.
 */
@RestController
@RequestMapping("/api/v1/meta")
class MetaController {

    record MetaResponse(String name, String version, String instance) {
    }

    private final MetaResponse meta;

    MetaController(ObjectProvider<BuildProperties> buildProperties,
                   @Value("${spring.application.name}") String name,
                   @Value("${HOSTNAME:local}") String instance) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.meta = new MetaResponse(name, build != null ? build.getVersion() : "dev", instance);
    }

    @GetMapping
    MetaResponse meta() {
        return meta;
    }
}
