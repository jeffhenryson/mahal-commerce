package com.cernecommerce.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que {@link PasswordPolicy#isValid} aplica todas as regras de complexidade
 * e que as constantes de comprimento estão alinhadas com a lógica.
 */
class PasswordPolicyTest {

    // ── happy paths ──────────────────────────────────────────────────────────

    @Test
    void valid_password_satisfies_all_rules() {
        assertThat(PasswordPolicy.isValid("Secure@1")).isTrue();
    }

    @Test
    void exactly_min_length_and_complex_passes() {
        // 8 caracteres com todos os requisitos
        assertThat(PasswordPolicy.isValid("Aa1!aaaa")).isTrue();
    }

    @Test
    void exactly_max_length_passes() {
        // 120 caracteres, todos os requisitos satisfeitos
        String password = "A1!" + "a".repeat(PasswordPolicy.MAX_LENGTH - 3);
        assertThat(password).hasSize(PasswordPolicy.MAX_LENGTH);
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    // ── null / comprimento ───────────────────────────────────────────────────

    @Test
    void null_password_fails() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
    }

    @Test
    void password_below_min_length_fails() {
        // 7 caracteres — um abaixo do mínimo, mas com todos os símbolos
        assertThat(PasswordPolicy.isValid("Aa1!aaa")).isFalse();
    }

    @Test
    void password_above_max_length_fails() {
        String tooLong = "A1!" + "a".repeat(PasswordPolicy.MAX_LENGTH - 2); // 121 chars
        assertThat(tooLong).hasSize(PasswordPolicy.MAX_LENGTH + 1);
        assertThat(PasswordPolicy.isValid(tooLong)).isFalse();
    }

    // ── requisitos de complexidade ───────────────────────────────────────────

    @Test
    void missing_uppercase_fails() {
        assertThat(PasswordPolicy.isValid("secure@1aaa")).isFalse();
    }

    @Test
    void missing_lowercase_fails() {
        assertThat(PasswordPolicy.isValid("SECURE@1AAA")).isFalse();
    }

    @Test
    void missing_digit_fails() {
        assertThat(PasswordPolicy.isValid("Secure@aaa")).isFalse();
    }

    @Test
    void missing_special_char_fails() {
        assertThat(PasswordPolicy.isValid("Secure1aaaa")).isFalse();
    }
}
