package com.mfg.ims.iam.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.service.AuthService;
import com.mfg.ims.iam.domain.Role;
import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.RoleRepository;
import com.mfg.ims.iam.repository.UserRepository;

/**
 * Creates the first administrator on a fresh installation, from the environment, and only when no user exists.
 * <p>
 * Reads {@code IMS_BOOTSTRAP_ADMIN_USERNAME} and {@code IMS_BOOTSTRAP_ADMIN_PASSWORD} (optionally
 * {@code IMS_BOOTSTRAP_ADMIN_EMAIL}). The password must satisfy the normal password policy and is stored as a
 * BCrypt hash; it is never logged. Without the variables nothing is created and a single line tells the operator
 * what to set. There is deliberately no default password anywhere in this path.
 */
@Component
public class InitialAdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuditService auditService;
    private final TransactionTemplate tx;
    private final String username;
    private final String password;
    private final String email;

    public InitialAdminBootstrap(UserRepository userRepo,
                                 RoleRepository roleRepo,
                                 PasswordEncoder passwordEncoder,
                                 AuthService authService,
                                 AuditService auditService,
                                 PlatformTransactionManager txManager,
                                 @Value("${ims.bootstrap.admin.username:}") String username,
                                 @Value("${ims.bootstrap.admin.password:}") String password,
                                 @Value("${ims.bootstrap.admin.email:}") String email) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(txManager);
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.email = email == null ? "" : email.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            tx.executeWithoutResult(status -> bootstrap());
        } catch (RuntimeException e) {
            // never stop the application over this; the operator can fix the variables and restart
            log.error("Initial administrator was not created: {}", e.getMessage());
        }
    }

    /** @return true when the administrator was created by this call. */
    boolean bootstrap() {
        if (userRepo.count() > 0) {
            return false;
        }
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("No users exist yet. Set IMS_BOOTSTRAP_ADMIN_USERNAME and IMS_BOOTSTRAP_ADMIN_PASSWORD "
                    + "and restart to create the first administrator.");
            return false;
        }

        authService.validateComplexity(password, username);
        Role adminRole = roleRepo.findByCode(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role " + ADMIN_ROLE + " is missing"));

        User admin = new User();
        admin.setUsername(username);
        admin.setFullName("Initial Administrator");
        admin.setEmail(email.isEmpty() ? username + "@localhost.localdomain" : email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setPasswordChangedAt(Instant.now());
        admin.setMustChangePassword(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepo.save(admin);

        auditService.record("CREATE", "USER", String.valueOf(admin.getId()), null, username,
                "Initial administrator created from the environment");
        log.info("Initial administrator '{}' created from the environment", username);
        return true;
    }
}
