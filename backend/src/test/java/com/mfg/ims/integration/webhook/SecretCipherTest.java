package com.mfg.ims.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.integration.webhook.service.SecretCipher;

class SecretCipherTest {

    private static byte[] key(int fill) {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) fill);
        return k;
    }

    @Test
    void roundTripsAndNeverStoresTheSecretInClear() {
        SecretCipher c = SecretCipher.withKey(key(7));
        String secret = "whsec_abcdefghijklmnopqrstuvwxyz0123456789";
        String stored = c.encrypt(secret);
        assertThat(stored).doesNotContain(secret).doesNotContain("whsec_");
        assertThat(c.decrypt(stored)).isEqualTo(secret);
    }

    @Test
    void everyEncryptionUsesAFreshIv() {
        SecretCipher c = SecretCipher.withKey(key(7));
        assertThat(c.encrypt("same")).isNotEqualTo(c.encrypt("same"));
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String stored = SecretCipher.withKey(key(7)).encrypt("secret");
        assertThatThrownBy(() -> SecretCipher.withKey(key(8)).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTamperedValueIsRejectedNotDecryptedToGarbage() {
        SecretCipher c = SecretCipher.withKey(key(7));
        byte[] raw = Base64.getDecoder().decode(c.encrypt("secret"));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> c.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingOrMalformedKeyFailsClosed() {
        for (String bad : new String[] {"", "   ", "not-base64!!", Base64.getEncoder().encodeToString(new byte[16])}) {
            SecretCipher c = new SecretCipher(bad);
            assertThat(c.isConfigured()).as("key '%s'", bad).isFalse();
            assertThatThrownBy(() -> c.encrypt("secret")).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("IMS_INTEGRATION_SECRET_KEY");
        }
    }

    @Test
    void aValidKeyIsConfigured() {
        assertThat(new SecretCipher(Base64.getEncoder().encodeToString(key(1))).isConfigured()).isTrue();
    }
}
