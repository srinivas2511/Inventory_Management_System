package com.mfg.ims.system;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.common.settings.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public liveness/identity endpoint used by the frontend shell and smoke tests. Exposes no business data. */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System")
public class SystemController {

    public record PingResponse(String status, String application, String version, Instant time) {}
    public record VersionResponse(String version, String appName, Instant time) {}

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<SystemSettingService> settings;

    public SystemController(ObjectProvider<BuildProperties> buildProperties, ObjectProvider<SystemSettingService> settings) {
        this.buildProperties = buildProperties;
        this.settings = settings;
    }

    @GetMapping("/ping")
    @PreAuthorize("permitAll()") // explicitly public; the architecture test requires every endpoint to declare access
    @SecurityRequirements // no bearer token needed
    @Operation(summary = "Liveness check and build version")
    public PingResponse ping() {
        BuildProperties build = buildProperties.getIfAvailable();
        return new PingResponse("UP", "ims", build != null ? build.getVersion() : "dev", Instant.now());
    }

    @GetMapping("/version")
    @PreAuthorize("permitAll()")
    @SecurityRequirements
    @Operation(summary = "Application version and name")
    public VersionResponse version() {
        BuildProperties build = buildProperties.getIfAvailable();
        SystemSettingService svc = settings.getIfAvailable();
        String version = build != null ? build.getVersion() : (svc != null ? svc.get("system.app_version", "dev") : "dev");
        String appName = svc != null ? svc.get("system.app_name", "ERP System") : "ERP System";
        return new VersionResponse(version, appName, Instant.now());
    }

    @GetMapping("/settings/public")
    @PreAuthorize("permitAll()")
    @SecurityRequirements
    @Operation(summary = "Public system settings visible to all clients")
    public Map<String, String> publicSettings() {
        SystemSettingService svc = settings.getIfAvailable();
        return Map.of(
            "appName",     svc != null ? svc.get("system.app_name", "ERP System") : "ERP System",
            "appVersion",  svc != null ? svc.get("system.app_version", "dev") : "dev",
            "companyName", svc != null ? svc.get("system.company_name", "Manufacturing Company") : "Manufacturing Company"
        );
    }
}
