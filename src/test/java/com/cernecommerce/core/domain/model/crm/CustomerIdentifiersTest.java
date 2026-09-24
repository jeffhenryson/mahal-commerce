package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIdentifiersTest {

    @Test
    void normalizeCpf_stripsMaskAndTurnsBlankIntoNull() {
        assertThat(CustomerIdentifiers.normalizeCpf("123.456.789-00")).isEqualTo("12345678900");
        assertThat(CustomerIdentifiers.normalizeCpf(" 12345678900 ")).isEqualTo("12345678900");
        assertThat(CustomerIdentifiers.normalizeCpf("")).isNull();
        assertThat(CustomerIdentifiers.normalizeCpf("   ")).isNull();
        assertThat(CustomerIdentifiers.normalizeCpf(null)).isNull();
    }

    @Test
    void normalizeCpf_rejectsWrongLength() {
        assertThatThrownBy(() -> CustomerIdentifiers.normalizeCpf("123.456.789"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeEmailAndContato_trimAndTurnBlankIntoNull() {
        assertThat(CustomerIdentifiers.normalizeEmail("")).isNull();
        assertThat(CustomerIdentifiers.normalizeEmail(" a@b.com ")).isEqualTo("a@b.com");
        assertThat(CustomerIdentifiers.normalizeContato(" (83) 99999-0000 ")).isEqualTo("(83) 99999-0000");
        assertThat(CustomerIdentifiers.normalizeContato(" ")).isNull();
    }

    @Test
    void digitsOrNull_keepsOnlyDigits() {
        assertThat(CustomerIdentifiers.digitsOrNull("+55 (83) 99999-0000")).isEqualTo("5583999990000");
        assertThat(CustomerIdentifiers.digitsOrNull("abc")).isNull();
    }
}
