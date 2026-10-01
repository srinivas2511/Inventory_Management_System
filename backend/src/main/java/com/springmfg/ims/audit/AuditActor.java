package com.springmfg.ims.audit;

import java.util.List;

/** Who did it, from where. {@code roles} are the roles held at the time of the action. */
public record AuditActor(Long userId, String username, List<String> roles, String ip, String correlationId) {

    /** For work no signed-in user performs: startup loaders, scheduled jobs. */
    public static AuditActor system(String ip, String correlationId) {
        return new AuditActor(null, "system", List.of(), ip, correlationId);
    }
}
