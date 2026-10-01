package com.springmfg.ims.admin;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.springmfg.ims.config.BootstrapAdminProperties;
import com.springmfg.ims.iam.UserRepository;

/**
 * Creates the first Admin on a brand-new installation so someone can sign in at all. It does nothing unless the
 * bootstrap properties are set, and nothing once any user exists, so restarting with the variables still set
 * never recreates or resets an account.
 */
@Component
@Order(10)
class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final UserAdminService userAdmin;

    BootstrapAdminInitializer(BootstrapAdminProperties properties, UserRepository users, UserAdminService userAdmin) {
        this.properties = properties;
        this.users = users;
        this.userAdmin = userAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.configured()) {
            return;
        }
        if (users.count() > 0) {
            log.info("Bootstrap admin skipped: users already exist");
            return;
        }
        userAdmin.create(new UserDtos.CreateUserRequest(properties.username(), null, properties.fullName(),
                properties.email(), null, Set.of(UserAdminService.ADMIN_ROLE), properties.password()));
        log.warn("Bootstrap admin '{}' created; it must change its password at first sign-in. "
                + "Remove ims.bootstrap.admin.* from the environment now.", properties.username());
    }
}
