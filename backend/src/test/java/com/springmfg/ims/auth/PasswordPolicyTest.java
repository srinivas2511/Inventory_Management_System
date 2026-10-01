package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordPolicyTest {

    private final SecuritySettings settings = mock(SecuritySettings.class);
    private final PasswordService service = new PasswordService(new BCryptPasswordEncoder(4), settings,
            mock(JdbcTemplate.class), Clock.systemUTC());

    PasswordPolicyTest() {
        when(settings.minPasswordLength()).thenReturn(12);
    }

    private List<String> violations(String password) {
        return service.violations(password, "operator1");
    }

    @Test
    void acceptsAStrongPassword() {
        assertThat(violations("Correct-Horse-9!")).isEmpty();
        assertThat(violations("Demo@123456!")).isEmpty(); // the documented demo password must be valid
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(violations("Aa1!aaaaaaa")).containsExactly("Must be at least 12 characters long."); // 11 chars
        assertThat(violations("Aa1!aaaaaaaa")).isEmpty(); // 12 chars
    }

    @Test
    void honoursTheConfiguredMinimum() {
        when(settings.minPasswordLength()).thenReturn(16);
        assertThat(violations("Aa1!aaaaaaaa")).containsExactly("Must be at least 16 characters long.");
    }

    @Test
    void requiresEachCharacterClass() {
        assertThat(violations("lowercase-only-1!")).containsExactly("Must contain an upper-case letter.");
        assertThat(violations("UPPERCASE-ONLY-1!")).containsExactly("Must contain a lower-case letter.");
        assertThat(violations("No-digits-here-at-all!")).containsExactly("Must contain a digit.");
        assertThat(violations("NoSymbolsHere12345")).containsExactly("Must contain a symbol.");
    }

    @Test
    void rejectsPasswordsContainingTheUsernameInAnyCase() {
        assertThat(violations("My-OPERATOR1-Pass9!")).containsExactly("Must not contain the username.");
    }

    @Test
    void reportsEveryViolationAtOnce() {
        assertThat(violations("abc")).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void rejectsPasswordsBcryptWouldTruncate() {
        String tooLong = "Aa1!" + "x".repeat(70);
        assertThat(violations(tooLong)).containsExactly("Must be at most 72 bytes long.");
    }
}
