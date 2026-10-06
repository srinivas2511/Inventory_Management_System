package com.springmfg.ims.integration.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signatures for outbound webhooks, in the {@code X-IMS-Signature} header as
 * {@code t=<unix seconds>,v1=<hex>}. The signed string is {@code t + "." + body}, so a captured request cannot be
 * replayed later: a receiver rejects a timestamp outside its tolerance (use {@link #verify}) and de-duplicates on the
 * {@code X-IMS-Event-Key} header.
 */
public final class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSigner() {
    }

    public static String signature(String secret, long timestampSeconds, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    public static String header(String secret, long timestampSeconds, String body) {
        return "t=" + timestampSeconds + ",v1=" + signature(secret, timestampSeconds, body);
    }

    /** Receiver-side check: valid signature and a timestamp within {@code toleranceSeconds} of {@code now}. */
    public static boolean verify(String secret, String header, String body, long toleranceSeconds, Instant now) {
        if (header == null) {
            return false;
        }
        Long timestamp = null;
        String provided = null;
        for (String part : header.split(",")) {
            String p = part.trim();
            if (p.startsWith("t=")) {
                try {
                    timestamp = Long.parseLong(p.substring(2));
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (p.startsWith("v1=")) {
                provided = p.substring(3);
            }
        }
        if (timestamp == null || provided == null) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - timestamp) > toleranceSeconds) {
            return false;
        }
        byte[] expected = signature(secret, timestamp, body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
    }
}
