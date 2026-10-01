package com.springmfg.ims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Pins the transaction interceptor <em>outside</em> the audit aspect (which uses the lowest precedence). The audit
 * row is then written inside the business transaction: if it cannot be written the change rolls back.
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true, order = Ordered.LOWEST_PRECEDENCE - 10)
public class TransactionConfig {
}
