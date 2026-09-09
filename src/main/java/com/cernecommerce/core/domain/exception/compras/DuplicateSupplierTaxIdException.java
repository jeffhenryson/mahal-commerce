package com.cernecommerce.core.domain.exception.compras;

/**
 * Já existe fornecedor com este CNPJ/CPF (COM-F001).
 *
 * <p>Erro tipado em vez do {@code DATA_INTEGRITY_VIOLATION} genérico que a {@code uk_supplier_tax_id}
 * (V58) devolveria: quem cadastra um fornecedor que já existe precisa ser levado até ele, não
 * receber "a operação conflita com um registro já existente".</p>
 */
public class DuplicateSupplierTaxIdException extends RuntimeException {
    public DuplicateSupplierTaxIdException(String taxId) {
        super("Já existe um fornecedor cadastrado com o CNPJ/CPF " + taxId);
    }
}
