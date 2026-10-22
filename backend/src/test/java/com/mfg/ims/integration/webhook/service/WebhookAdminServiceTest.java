package com.mfg.ims.integration.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mfg.ims.common.exception.BusinessRuleException;

class WebhookAdminServiceTest {

    @Test
    void eventListsAreCheckedDeduplicatedAndStoredInOrder() {
        assertThat(WebhookAdminService.normaliseEvents(List.of("StockPosted", " InvoicePaid ", "StockPosted")))
                .isEqualTo("StockPosted,InvoicePaid");
        assertThat(WebhookAdminService.normaliseEvents(List.of("OrderCompleted"))).isEqualTo("OrderCompleted");
    }

    @Test
    void theWildcardWinsAndUnknownEventsAreRefused() {
        assertThat(WebhookAdminService.normaliseEvents(List.of("*"))).isEqualTo("*");
        assertThat(WebhookAdminService.normaliseEvents(List.of("StockPosted", "*"))).isEqualTo("*");
        assertThatThrownBy(() -> WebhookAdminService.normaliseEvents(List.of("StockPosted", "Bogus")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Bogus");
        assertThatThrownBy(() -> WebhookAdminService.normaliseEvents(List.of("Ping")))
                .as("Ping is only sent by the test action, never subscribed to")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void generatedSecretsAreLongRandomAndUrlSafe() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String s = WebhookAdminService.newSecret();
            assertThat(s).startsWith("whsec_").hasSize(6 + 43).matches("whsec_[A-Za-z0-9_-]{43}");
            assertThat(seen.add(s)).as("secrets are unique").isTrue();
        }
    }

    @Test
    void subscriptionsMatchTheirEventListOrTheWildcard() {
        assertThat(WebhookEventTypes.matches("*", "StockPosted")).isTrue();
        assertThat(WebhookEventTypes.matches("StockPosted", "StockPosted")).isTrue();
        assertThat(WebhookEventTypes.matches("OrderCompleted,InvoicePaid", "InvoicePaid")).isTrue();
        assertThat(WebhookEventTypes.matches("OrderCompleted,InvoicePaid", "StockPosted")).isFalse();
        assertThat(WebhookEventTypes.matches("StockPostedX", "StockPosted")).as("no substring matches").isFalse();
        assertThat(WebhookEventTypes.matches(null, "StockPosted")).isFalse();
    }
}
