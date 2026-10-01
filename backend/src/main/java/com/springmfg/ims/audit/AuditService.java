package com.springmfg.ims.audit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.springmfg.ims.auth.AuthenticatedUser;
import com.springmfg.ims.auth.CurrentUser;
import com.springmfg.ims.common.exception.Problems;

/**
 * Writes {@code audit_logs} rows (ARCHITECTURE.md section 11). {@link #record} joins the caller's transaction, so
 * if the audit insert fails the business change rolls back with it. The table is append-only (database trigger).
 */
@Service
public class AuditService {

    static final String MASK = "***";
    private static final Set<String> SECRET_KEYS = Set.of("password", "passwordhash", "password_hash",
            "temporarypassword", "currentpassword", "newpassword", "token", "refreshtoken", "accesstoken", "secret");
    private static final int MAX_ROLES = 200;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    AuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Records {@code command} as the signed-in user, or as {@code system} when nobody is signed in. */
    @Transactional
    public void record(AuditCommand command) {
        record(currentActor(), command);
    }

    @Transactional
    public void record(AuditActor actor, AuditCommand command) {
        if (command.action() == null || command.entity() == null) {
            throw new IllegalArgumentException("An audit record needs an action and an entity");
        }
        jdbc.update("""
                INSERT INTO audit_logs (user_id, username, roles, action, entity, entity_id, old_value, new_value,
                                        reason, ip_address, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)""",
                actor.userId(), truncate(actor.username(), 50), truncate(String.join(",", actor.roles()), MAX_ROLES),
                command.action(), command.entity(), truncate(command.entityId(), 60),
                toJson(command.oldValue()), toJson(command.newValue()), truncate(command.reason(), 500),
                truncate(actor.ip(), 45), truncate(actor.correlationId(), 40));
    }

    /** The actor of the current request: user and roles from the security context, IP and trace id from the request. */
    public AuditActor currentActor() {
        final String ip;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            ip = request.getRemoteAddr();
        } else {
            ip = null;
        }
        String correlationId = MDC.get(Problems.TRACE_ID_KEY);
        return CurrentUser.get()
                .map((AuthenticatedUser u) -> new AuditActor(u.id(), u.username(), u.roles(), ip, correlationId))
                .orElseGet(() -> AuditActor.system(ip, correlationId));
    }

    /** {@code null} stays SQL NULL; secret-looking keys are masked at any depth. */
    String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(mask(json.valueToTree(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit snapshot is not serialisable", e);
        }
    }

    static JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new java.util.ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (isSecret(name)) {
                    object.put(name, MASK);
                } else {
                    mask(object.get(name));
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(AuditService::mask);
        }
        return node;
    }

    private static boolean isSecret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return SECRET_KEYS.contains(k) || k.endsWith("hash") || k.endsWith("secret") || k.endsWith("token");
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
