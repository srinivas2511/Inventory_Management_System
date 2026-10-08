package com.springmfg.ims.auth.stepup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Re-authentication by entering the account password again. The password is checked and never stored or logged. */
@Component
public class PasswordStepUpMethod implements StepUpMethod {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public PasswordStepUpMethod(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String name() {
        return "PASSWORD";
    }

    @Override
    public boolean verify(Long userId, String credential) {
        if (credential == null || credential.isBlank()) {
            return false;
        }
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM ims.users WHERE id = ? AND active = TRUE",
                String.class, userId);
        return !hashes.isEmpty() && passwordEncoder.matches(credential, hashes.get(0));
    }
}
