package com.springmfg.ims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * First-install Admin account. Active only when {@code email} and {@code password} are both set and no user
 * exists yet; the account must change the password at first sign-in. Unset them after the first start.
 *
 * @param username Admin user name
 * @param fullName display name
 * @param email    e-mail address (also used for password reset)
 * @param password temporary password; must satisfy the password policy
 */
@ConfigurationProperties(prefix = "ims.bootstrap.admin")
public record BootstrapAdminProperties(
        @DefaultValue("admin") String username,
        @DefaultValue("Administrator") String fullName,
        String email,
        String password) {

    public boolean configured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
