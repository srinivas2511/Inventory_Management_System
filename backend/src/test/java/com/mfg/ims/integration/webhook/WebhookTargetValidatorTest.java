package com.mfg.ims.integration.webhook;

import com.mfg.ims.common.net.WebhookTargetValidator;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mfg.ims.common.exception.BusinessRuleException;

/** IP literals only, so the tests do not depend on DNS. */
class WebhookTargetValidatorTest {

    private final WebhookTargetValidator strict = new WebhookTargetValidator(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/hook",                    // loopback
            "https://127.1.2.3/hook",
            "https://[::1]/hook",                        // IPv6 loopback
            "https://10.1.2.3/hook",                     // private
            "https://172.16.0.1/hook",
            "https://172.31.255.254/hook",
            "https://192.168.1.1/hook",
            "https://169.254.169.254/latest/meta-data",  // cloud metadata (link-local)
            "https://[fe80::1]/hook",                    // IPv6 link-local
            "https://[fd00::1]/hook",                    // IPv6 unique local
            "https://[fc00::1]/hook",
            "https://100.64.0.1/hook",                   // carrier-grade NAT
            "https://100.127.255.254/hook",
            "https://0.0.0.0/hook",                      // unspecified
            "https://0.1.2.3/hook",
            "https://224.0.0.1/hook",                    // multicast
            "https://240.0.0.1/hook",                    // reserved
            "https://255.255.255.255/hook",              // broadcast
            "https://198.18.0.1/hook",                   // benchmarking
            "https://192.0.0.5/hook",                    // IETF protocol assignments
            "https://[::ffff:127.0.0.1]/hook",           // IPv4-mapped loopback
    })
    void nonPublicAddressesAreRefused(String url) {
        assertThatThrownBy(() -> strict.validate(url))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Webhook target refused");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://8.8.8.8/hook",              // plain http
            "ftp://8.8.8.8/hook",
            "file:///etc/passwd",
            "gopher://8.8.8.8/",
            "https://user:secret@8.8.8.8/hook", // credentials in the URL
            "https://8.8.8.8/hook#fragment",
            "https:///nohost",
            "not a url",
            "https://8.8.8.8:0/hook",
            "https://8.8.8.8:70000/hook",
    })
    void badSchemesCredentialsAndShapesAreRefused(String url) {
        assertThatThrownBy(() -> strict.validate(url)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://8.8.8.8/hook",
            "https://8.8.4.4:8443/path?x=1",
            "https://[2001:4860:4860::8888]/hook",
            "https://1.1.1.1/",
    })
    void publicHttpsTargetsAreAccepted(String url) {
        assertThatCode(() -> strict.validate(url)).doesNotThrowAnyException();
    }

    @Test
    void developmentModeAllowsLocalhostHttpButStillRefusesOtherSchemes() {
        WebhookTargetValidator dev = new WebhookTargetValidator(true);
        assertThatCode(() -> dev.validate("http://127.0.0.1:8080/hook")).doesNotThrowAnyException();
        assertThatCode(() -> dev.validate("https://10.0.0.5/hook")).doesNotThrowAnyException();
        assertThatThrownBy(() -> dev.validate("ftp://127.0.0.1/hook")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> dev.validate("https://user:pw@127.0.0.1/hook")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theRefusalNeverEchoesTheResolvedAddress() {
        assertThatThrownBy(() -> strict.validate("https://10.9.8.7/hook"))
                .hasMessageNotContaining("10.9.8.7");
    }
}
