package com.springmfg.ims.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The application's {@link Clock} in integration tests. It follows the system clock until a test pins it with
 * {@link #pin(Instant)}, which lets a test make an operation take exactly two hours. Every test that pins it must
 * call {@link #release()} afterwards (the shared Spring context outlives the test).
 */
public class TestClock extends Clock {

    private volatile Instant pinned;

    public void pin(Instant instant) {
        this.pinned = instant;
    }

    public void release() {
        this.pinned = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant p = pinned;
        return p != null ? p : Instant.now();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public TestClock testClock() {
            return new TestClock();
        }
    }
}
