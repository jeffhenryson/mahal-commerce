package com.cernecommerce.core.domain.exception.compras;

/**
 * Nenhum fornecedor cadastrado bate com o CNPJ do emitente da NF-e importada (EST-F005).
 *
 * <p>Deliberadamente <b>não</b> cria o fornecedor automaticamente — diferente do precedente de
 * Categoria (taxonomia de baixo risco, criada sob demanda). {@code Supplier.taxId} alimenta conta
 * a pagar/compliance no futuro; criar a partir de XML não revisado é dado demais para aceitar sem
 * revisão humana. Cadastro de fornecedor (COM-F001) é limitação conhecida, fora desta entrega.
 */
public class SupplierNotFoundByTaxIdException extends RuntimeException {
    public SupplierNotFoundByTaxIdException(String taxId) {
        super("Nenhum fornecedor cadastrado com o CNPJ " + taxId
                + ". Cadastre o fornecedor antes de importar a NF-e.");
    }
}
