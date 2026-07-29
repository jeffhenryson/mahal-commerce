package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    @Test
    void create_buildsCustomerWithoutIdAndWithCadastradoEmSet() {
        Customer customer = Customer.create("Maria Silva", "11999998888", "maria@example.com", "12345678900", "loja-fisica");

        assertThat(customer.id()).isNull();
        assertThat(customer.nome()).isEqualTo("Maria Silva");
        assertThat(customer.contato()).isEqualTo("11999998888");
        assertThat(customer.email()).isEqualTo("maria@example.com");
        assertThat(customer.cpf()).isEqualTo("12345678900");
        assertThat(customer.origem()).isEqualTo("loja-fisica");
        assertThat(customer.cadastradoEm()).isNotNull();
        assertThat(customer.estagio()).isEqualTo(CustomerStage.NOVO_LEAD);
    }

    @Test
    void create_allowsNullCpfAndOrigem() {
        Customer customer = Customer.create("Maria Silva", "11999998888", "maria@example.com", null, null);

        assertThat(customer.cpf()).isNull();
        assertThat(customer.origem()).isNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant cadastradoEm = Instant.parse("2026-01-01T00:00:00Z");
        Customer customer = Customer.of(1L, "Maria Silva", "11999998888", "maria@example.com", "12345678900",
                "loja-fisica", cadastradoEm, CustomerStage.QUALIFICADO);

        assertThat(customer.id()).isEqualTo(1L);
        assertThat(customer.cadastradoEm()).isEqualTo(cadastradoEm);
        assertThat(customer.estagio()).isEqualTo(CustomerStage.QUALIFICADO);
    }

    @Test
    void withEstagio_returnsNewCustomerWithUpdatedStage() {
        Customer customer = Customer.create("Maria Silva", "11999998888", "maria@example.com", null, null);

        Customer moved = customer.withEstagio(CustomerStage.EM_ATENDIMENTO);

        assertThat(moved.estagio()).isEqualTo(CustomerStage.EM_ATENDIMENTO);
        assertThat(moved.id()).isEqualTo(customer.id());
        assertThat(customer.estagio()).isEqualTo(CustomerStage.NOVO_LEAD);
    }

    @Test
    void throwsWhenNomeIsBlank() {
        assertThatThrownBy(() -> Customer.create("  ", "11999998888", "maria@example.com", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenEmailHasInvalidFormat() {
        assertThatThrownBy(() -> Customer.create("Maria Silva", "11999998888", "email-invalido", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── CRM-C005: cpf é o identificador oficial; email e contato são alternativos ───────────

    @Test
    void allowsContatoBlankWhenEmailIsPresent() {
        Customer customer = Customer.create("Maria Silva", " ", "maria@example.com", null, null);

        assertThat(customer.contato()).isBlank();
        assertThat(customer.email()).isEqualTo("maria@example.com");
    }

    @Test
    void allowsEmailBlankWhenContatoIsPresent() {
        Customer customer = Customer.create("Maria Silva", "11999998888", " ", null, null);

        assertThat(customer.email()).isBlank();
        assertThat(customer.contato()).isEqualTo("11999998888");
    }

    /** "Cliente leve": nenhum contato nem email, só CPF — é o cadastro oficial por si só. */
    @Test
    void allowsContatoAndEmailBlankWhenCpfIsPresent() {
        Customer customer = Customer.create("Maria Silva", null, null, "12345678900", null);

        assertThat(customer.contato()).isNull();
        assertThat(customer.email()).isNull();
        assertThat(customer.cpf()).isEqualTo("12345678900");
    }

    @Test
    void throwsWhenNoIdentifierAtAll() {
        assertThatThrownBy(() -> Customer.create("Maria Silva", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpf, email ou contato");
        assertThatThrownBy(() -> Customer.create("Maria Silva", " ", " ", " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpf, email ou contato");
    }

    @Test
    void isOfficiallyRegistered_trueOnlyWithCpf() {
        Customer comCpf = Customer.create("Maria Silva", null, null, "12345678900", null);
        Customer semCpf = Customer.create("Maria Silva", "11999998888", null, null, null);

        assertThat(comCpf.isOfficiallyRegistered()).isTrue();
        assertThat(semCpf.isOfficiallyRegistered()).isFalse();
    }
}
