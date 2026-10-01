package com.springmfg.ims.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** One injectable clock so time-dependent rules (lockout, token expiry) are testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
