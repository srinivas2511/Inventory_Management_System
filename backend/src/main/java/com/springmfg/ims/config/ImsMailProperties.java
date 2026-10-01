package com.springmfg.ims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param from         sender address
 * @param resetUrlBase public base URL of the web app; the reset link is {@code <base>/reset-password/<token>}
 */
@ConfigurationProperties(prefix = "ims.mail")
public record ImsMailProperties(
        @DefaultValue("no-reply@springmfg.local") String from,
        @DefaultValue("http://localhost:4200") String resetUrlBase) {
}
