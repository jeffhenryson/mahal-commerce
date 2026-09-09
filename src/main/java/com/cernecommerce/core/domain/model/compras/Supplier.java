package com.cernecommerce.core.domain.model.compras;

/**
 * Fornecedor de mercadorias. Base para pedidos de compra e entradas de estoque.
 *
 * <p><b>COM-F001.</b> Era um record sem nenhuma invariante — o único do domínio, ao lado de
 * {@code Customer}, {@code Order} e {@code CustomerNote}, que todos validam — porque só existia
 * leitura: {@code GET /compras/suppliers} devolvia o que o banco tivesse, e quem gravava era
 * {@code INSERT} manual. Com a escrita entrando, as regras precisam morar aqui.</p>
 *
 * <p><b>{@code taxId} é normalizado para só dígitos</b>, e isso não é cosmético. O
 * {@code findByTaxId} é comparação exata de string, e é ele que a importação de NF-e usa para achar
 * o emitente da nota — que chega do XML sem máscara. Aceitar {@code "12.345.678/0001-99"} e
 * {@code "12345678000199"} como valores distintos criaria dois fornecedores para o mesmo CNPJ, com
 * a {@code uk_supplier_tax_id} sem enxergar a duplicidade. É exatamente o buraco que
 * {@code Customer.cpf} tem hoje e que aqui não se repete.</p>
 */
public record Supplier(
    Long id,
    String legalName,
    String taxId,
    String email,
    boolean active
) {

    public Supplier {
        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException("legalName é obrigatório");
        }
        if (legalName.length() > 150) {
            throw new IllegalArgumentException("legalName excede 150 caracteres");
        }
        // Normaliza sempre, valida o tamanho só na CRIAÇÃO (ver create). O compact constructor é
        // também o caminho de reconstituição a partir do banco, e a coluna é VARCHAR(20) desde a
        // V58: pode haver linha antiga gravada com máscara por INSERT manual — que era o único
        // jeito de cadastrar fornecedor até COM-F001. Recusá-la aqui derrubaria a leitura de dados
        // que já existem, trocando um cadastro feio por um GET quebrado.
        taxId = digitsOf(taxId);
        if (email != null && email.isBlank()) {
            email = null;
        }
        if (email != null && email.length() > 150) {
            throw new IllegalArgumentException("email excede 150 caracteres");
        }
    }

    /** Fornecedor novo nasce ativo — mesma convenção de {@code Product} e {@code Warehouse}. */
    public static Supplier create(String legalName, String taxId, String email) {
        String digits = digitsOf(taxId);
        // CNPJ (14) ou CPF (11) — o produtor rural que emite nota é pessoa física, e recusá-lo
        // fecharia a porta para um fornecedor legítimo. Sem dígito verificador de propósito: a
        // nota fiscal que traz esse número já foi validada pela SEFAZ, e reprovar aqui um CNPJ que
        // o fisco aceitou travaria o recebimento por causa de uma regra nossa.
        if (digits.length() != 11 && digits.length() != 14) {
            throw new IllegalArgumentException(
                    "taxId deve ter 11 dígitos (CPF) ou 14 (CNPJ): " + digits.length());
        }
        return new Supplier(null, legalName, digits, email, true);
    }

    /** PATCH parcial: nulo mantém, mesma semântica de {@code EstoqueUseCase.updateProduct}. */
    public Supplier updatedWith(String newLegalName, String newEmail) {
        return new Supplier(id,
                newLegalName == null ? legalName : newLegalName,
                taxId,
                newEmail == null ? email : newEmail,
                active);
    }

    public Supplier withActive(boolean newActive) {
        return new Supplier(id, legalName, taxId, email, newActive);
    }

    private static String digitsOf(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("taxId é obrigatório");
        }
        return value.replaceAll("\\D", "");
    }
}
