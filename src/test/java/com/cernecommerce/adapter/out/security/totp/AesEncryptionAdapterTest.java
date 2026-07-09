package com.cernecommerce.adapter.out.security.totp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptionAdapterTest {

    private AesEncryptionAdapter adapter;

    @BeforeEach
    void setup() {
        // 32 bytes zerados encodados em base64 — válido para testes
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        adapter = new AesEncryptionAdapter(key);
    }

    @Test
    void encrypt_then_decrypt_returns_original() {
        String plaintext = "JBSWY3DPEHPK3PXP"; // TOTP secret típico
        String encrypted = adapter.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(adapter.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_same_plaintext_produces_different_ciphertexts() {
        // IV aleatório garante que dois encrypts do mesmo texto sejam diferentes
        String a = adapter.encrypt("secret");
        String b = adapter.encrypt("secret");
        assertThat(a).isNotEqualTo(b);
        assertThat(adapter.decrypt(a)).isEqualTo("secret");
        assertThat(adapter.decrypt(b)).isEqualTo("secret");
    }

    @Test
    void decrypt_tampered_ciphertext_throws() {
        String encrypted = adapter.encrypt("my-secret");
        // Altera um byte no meio do ciphertext para simular adulteração
        byte[] bytes = Base64.getUrlDecoder().decode(encrypted);
        bytes[bytes.length / 2] ^= 0xFF;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertThatThrownBy(() -> adapter.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void constructor_rejects_key_with_wrong_length() {
        String shortKey = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesEncryptionAdapter(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void encrypt_and_decrypt_unicode_plaintext() {
        String unicode = "sécret-TOTP-Ação";
        assertThat(adapter.decrypt(adapter.encrypt(unicode))).isEqualTo(unicode);
    }
}
