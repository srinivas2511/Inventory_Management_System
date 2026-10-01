package com.springmfg.ims.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.springmfg.ims.auth.PasswordService;
import com.springmfg.ims.iam.Role;
import com.springmfg.ims.iam.RoleRepository;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.iam.UserRepository;

/** Creates users for integration tests: unique names, a seeded role, the first password in the history. */
@Component
public class TestUsers {

    public static final String PASSWORD = "Initial-Pass-1234!";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final PasswordService passwords;
    private final TransactionTemplate tx;

    public TestUsers(UserRepository users, RoleRepository roles, PasswordEncoder encoder, PasswordService passwords,
            TransactionTemplate tx) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.passwords = passwords;
        this.tx = tx;
    }

    /** An active user with the given role whose password is {@link #PASSWORD} and who need not change it. */
    public User create(String roleCode) {
        return create(roleCode, false);
    }

    public User create(String roleCode, boolean mustChangePassword) {
        int n = COUNTER.incrementAndGet();
        return tx.execute(status -> {
            User user = new User("tuser" + n, "Test User " + n, "tuser" + n + "@example.com", encoder.encode(PASSWORD));
            Role role = roles.findByCode(roleCode).orElseThrow();
            user.getRoles().add(role);
            if (!mustChangePassword) {
                user.changePassword(user.getPasswordHash(), java.time.Instant.now());
            }
            User saved = users.saveAndFlush(user);
            passwords.recordCurrentPassword(saved);
            return saved;
        });
    }
}
