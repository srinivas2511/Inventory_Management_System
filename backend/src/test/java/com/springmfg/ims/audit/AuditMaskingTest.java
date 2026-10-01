package com.springmfg.ims.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.auth.AuthEvent;

class AuditMaskingTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AuditService service = new AuditService(mock(JdbcTemplate.class), json);

    @Test
    void secretLookingKeysAreMaskedAtAnyDepth() throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("username", "u1");
        value.put("password", "hunter2");
        value.put("passwordHash", "$2a$12$abc");
        value.put("temporaryPassword", "Temp-1!");
        value.put("mustChangePassword", true); // a flag, not a secret
        value.put("nested", Map.of("refreshToken", "t0k3n", "ok", "fine"));
        value.put("list", List.of(Map.of("apiSecret", "s", "name", "n")));

        String stored = service.toJson(value);

        assertThat(stored).doesNotContain("hunter2").doesNotContain("$2a$").doesNotContain("Temp-1!")
                .doesNotContain("t0k3n").doesNotContain("\"s\"");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = json.readValue(stored, Map.class);
        assertThat(parsed).containsEntry("username", "u1").containsEntry("mustChangePassword", true)
                .containsEntry("password", "***").containsEntry("passwordHash", "***");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) parsed.get("nested");
        assertThat(nested).containsEntry("refreshToken", "***").containsEntry("ok", "fine");
    }

    @Test
    void nullSnapshotsStaySqlNull() {
        assertThat(service.toJson(null)).isNull();
    }

    @Test
    void anAuditRecordNeedsAnActionAndAnEntity() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(AuditActor.system(null, null),
                new AuditCommand(null, "User", "1", null, null, null))).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(AuditActor.system(null, null),
                new AuditCommand("X", null, "1", null, null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedSignInsWithImplausibleUsernamesAreNotStoredVerbatim() {
        AuditService audit = mock(AuditService.class);
        AuditEventListener listener = new AuditEventListener(audit);

        listener.on(new AuthEvent(AuthEvent.Type.LOGIN_FAILURE, null, "Demo@123456!", List.of(), "10.0.0.1", "unknown user"));
        listener.on(new AuthEvent(AuthEvent.Type.LOGIN_FAILURE, null, "ghost.user", List.of(), "10.0.0.1", "unknown user"));
        listener.on(new AuthEvent(AuthEvent.Type.LOGIN_SUCCESS, 7L, "operator1", List.of("OPERATOR"), "10.0.0.2", null));

        ArgumentCaptor<AuditActor> actors = ArgumentCaptor.forClass(AuditActor.class);
        ArgumentCaptor<AuditCommand> commands = ArgumentCaptor.forClass(AuditCommand.class);
        verify(audit, org.mockito.Mockito.times(3)).record(actors.capture(), commands.capture());

        assertThat(actors.getAllValues().get(0).username()).isEqualTo("(invalid)");
        assertThat(actors.getAllValues().get(1).username()).isEqualTo("ghost.user");
        assertThat(actors.getAllValues().get(2).username()).isEqualTo("operator1");
        assertThat(actors.getAllValues().get(2).roles()).containsExactly("OPERATOR");
        assertThat(actors.getAllValues().get(2).userId()).isEqualTo(7L);
        assertThat(commands.getAllValues().get(0).action()).isEqualTo("LOGIN_FAILURE");
        assertThat(commands.getAllValues().get(0).newValue()).containsEntry("detail", "unknown user");
        assertThat(commands.getAllValues().get(2).entityId()).isEqualTo("7");
        assertThat(commands.getAllValues().get(2).newValue()).isNull();
    }

    @Test
    void auditCommandsAreForwardedAsTheyAre() {
        AuditService audit = mock(AuditService.class);
        AuditCommand command = new AuditCommand("USER_CREATED", "User", "1", null, Map.of("a", 1), "why");
        new AuditEventListener(audit).on(command);
        verify(audit).record(eq(command));
        verify(audit, org.mockito.Mockito.never()).record(any(AuditActor.class), any());
    }
}
