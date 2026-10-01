package com.springmfg.ims.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.springmfg.ims.auth.AuthEvent;
import com.springmfg.ims.common.exception.Problems;

/**
 * Turns published events into audit rows. Both listeners are synchronous, so they run in the publisher's
 * transaction (CLAUDE.md rule 7); a failure here fails the operation that raised the event.
 */
@Component
class AuditEventListener {

    /** What a user name can look like. Anything else typed on a login form may be a password in the wrong field. */
    private static final Pattern PLAUSIBLE_USERNAME = Pattern.compile("[A-Za-z0-9._-]{1,50}");

    private final AuditService audit;

    AuditEventListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    void on(AuditCommand command) {
        audit.record(command);
    }

    @EventListener
    void on(AuthEvent event) {
        String username = event.username();
        if (event.userId() == null && (username == null || !PLAUSIBLE_USERNAME.matcher(username).matches())) {
            username = "(invalid)"; // never keep arbitrary text from a failed sign-in: it might be someone's password
        }
        Map<String, Object> detail = null;
        if (event.detail() != null) {
            detail = new LinkedHashMap<>();
            detail.put("detail", event.detail());
        }
        AuditActor actor = new AuditActor(event.userId(), username, event.roles(), event.ip(), MDC.get(Problems.TRACE_ID_KEY));
        audit.record(actor, new AuditCommand(event.type().name(), "User",
                event.userId() == null ? null : String.valueOf(event.userId()), null, detail, null));
    }
}
