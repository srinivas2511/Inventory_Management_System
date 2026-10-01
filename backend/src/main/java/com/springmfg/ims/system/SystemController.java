package com.springmfg.ims.system;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public liveness/identity endpoint used by the frontend shell and smoke tests. Exposes no business data. */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System")
public class SystemController {

    public record PingResponse(String status, String application, String version, Instant time) {
    }

    private final ObjectProvider<BuildProperties> buildProperties;

    public SystemController(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/ping")
    @PreAuthorize("permitAll()") // explicitly public; the architecture test requires every endpoint to declare access
    @SecurityRequirements // no bearer token needed
    @Operation(summary = "Liveness check and build version")
    public PingResponse ping() {
        BuildProperties build = buildProperties.getIfAvailable();
        return new PingResponse("UP", "ims", build != null ? build.getVersion() : "dev", Instant.now());
    }
}
