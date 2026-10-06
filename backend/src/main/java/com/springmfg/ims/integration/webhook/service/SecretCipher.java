package com.springmfg.ims.integration.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/**
 * AES-256-GCM for webhook secrets at rest. The key is 32 random bytes, base64-encoded, supplied through the
 * environment ({@code IMS_INTEGRATION_SECRET_KEY}); it is never stored in the database and a missing or malformed key
 * fails closed, so a secret is never written in clear. Each value gets a fresh 96-bit IV.
 */
@Component
public class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    public SecretCipher(@Value("${ims.integration.secret-key:}") String base64Key) {
        this.key = decodeKey(base64Key);
    }

    /** For unit tests, which supply the key directly. */
    public static SecretCipher withKey(byte[] key) {
        return new SecretCipher(Base64.getEncoder().encodeToString(key));
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        byte[] k = requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the secret", e);
        }
    }

    public String decrypt(String encoded) {
        byte[] k = requireKey();
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(in, 0, IV_BYTES);
            byte[] ct = Arrays.copyOfRange(in, IV_BYTES, in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt the secret (wrong key or corrupted value)", e);
        }
    }

    private byte[] requireKey() {
        if (key == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Webhook secrets cannot be stored: IMS_INTEGRATION_SECRET_KEY (32 bytes, base64) is not configured");
        }
        return key;
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
