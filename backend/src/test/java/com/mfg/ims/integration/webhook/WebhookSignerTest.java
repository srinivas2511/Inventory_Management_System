package com.mfg.ims.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.mfg.ims.integration.webhook.service.WebhookSigner;

class WebhookSignerTest {

    private static final String SECRET = "whsec_unit-test-secret";
    private static final String BODY = "{\"id\":\"StockPosted:7\",\"type\":\"StockPosted\"}";
    private static final long T = 1_760_000_000L;

    /** An independent computation of HMAC-SHA256 over "t.body", so the signer is not checked against itself. */
    private static String reference(String secret, long t, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void signatureMatchesAnIndependentHmac() throws Exception {
        assertThat(WebhookSigner.signature(SECRET, T, BODY)).isEqualTo(reference(SECRET, T, BODY));
        assertThat(WebhookSigner.header(SECRET, T, BODY)).isEqualTo("t=" + T + ",v1=" + reference(SECRET, T, BODY));
    }

    @Test
    void aFreshValidSignatureVerifies() {
        String header = WebhookSigner.header(SECRET, T, BODY);
        assertThat(WebhookSigner.verify(SECRET, header, BODY, 300, Instant.ofEpochSecond(T + 10))).isTrue();
    }

    @Test
    void aTamperedBodyOrWrongSecretFails() {
        String header = WebhookSigner.header(SECRET, T, BODY);
        Instant now = Instant.ofEpochSecond(T);
        assertThat(WebhookSigner.verify(SECRET, header, BODY + " ", 300, now)).isFalse();
        assertThat(WebhookSigner.verify("another-secret", header, BODY, 300, now)).isFalse();
    }

    @Test
    void aCapturedRequestCannotBeReplayedLater() {
        String header = WebhookSigner.header(SECRET, T, BODY);
        // valid at the time, rejected once the timestamp is older (or newer) than the tolerance
        assertThat(WebhookSigner.verify(SECRET, header, BODY, 300, Instant.ofEpochSecond(T + 300))).isTrue();
        assertThat(WebhookSigner.verify(SECRET, header, BODY, 300, Instant.ofEpochSecond(T + 301))).isFalse();
        assertThat(WebhookSigner.verify(SECRET, header, BODY, 300, Instant.ofEpochSecond(T - 301))).isFalse();
    }

    @Test
    void forgingTheTimestampBreaksTheSignature() {
        String header = WebhookSigner.header(SECRET, T, BODY);
        String v1 = header.substring(header.indexOf("v1="));
        // same signature, a later timestamp: the signed string changes, so it no longer verifies
        String moved = "t=" + (T + 100) + "," + v1;
        assertThat(WebhookSigner.verify(SECRET, moved, BODY, 300, Instant.ofEpochSecond(T + 100))).isFalse();
    }

    @Test
    void malformedHeadersFail() {
        Instant now = Instant.ofEpochSecond(T);
        assertThat(WebhookSigner.verify(SECRET, null, BODY, 300, now)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "", BODY, 300, now)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "v1=abcd", BODY, 300, now)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "t=notanumber,v1=abcd", BODY, 300, now)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "t=" + T, BODY, 300, now)).isFalse();
    }
}
