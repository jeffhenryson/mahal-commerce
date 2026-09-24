package com.cernecommerce.core.domain.model.crm;

/**
 * Normalização dos identificadores de cliente (CRM-C006) — um ponto só para create, update e o
 * find-or-create de lead, para que "123.456.789-00" e "12345678901" sejam o mesmo CPF e um campo
 * deixado em branco pelo formulário ({@code ""}) não vire valor gravado.
 *
 * <p>Motivo concreto: {@code email = ""} passava no {@code @Email} e era salvo; o segundo cliente
 * "leve" com email vazio batia em {@code uk_customers_email} e tomava 409. E CPF com máscara era
 * recusado com 400 pelo {@code @Size(11)} do DTO.</p>
 */
public final class CustomerIdentifiers {

    public static final int CPF_LENGTH = 11;

    private CustomerIdentifiers() {
    }

    /** Só os dígitos do CPF; {@code null} quando não sobra nenhum. Rejeita tamanho diferente de 11. */
    public static String normalizeCpf(String cpf) {
        String digits = digitsOrNull(cpf);
        if (digits != null && digits.length() != CPF_LENGTH) {
            throw new IllegalArgumentException("cpf deve ter 11 dígitos");
        }
        return digits;
    }

    /** Email aparado; {@code null} quando em branco. */
    public static String normalizeEmail(String email) {
        return blankToNull(email);
    }

    /**
     * Contato aparado, com a formatação que o operador digitou — é o que aparece na tela. A
     * comparação entre contatos usa {@link #digitsOrNull(String)}.
     */
    public static String normalizeContato(String contato) {
        return blankToNull(contato);
    }

    /** Só os dígitos; {@code null} quando não sobra nenhum. */
    public static String digitsOrNull(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
