package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Edição parcial do fornecedor (COM-F001) — campo ausente mantém o valor atual, mesma semântica
 * de {@code ProductPatchRequest}.
 *
 * <p>{@code taxId} não está aqui de propósito: é a chave pela qual a importação de NF-e encontra
 * o fornecedor, e trocá-lo faria os recebimentos já registrados apontarem para um CNPJ que nunca
 * os emitiu. Mesma razão pela qual o SKU do produto também ficou fora do PATCH.</p>
 */
@Data
public class SupplierPatchRequest {

    @Size(max = 150)
    @Schema(description = "Nova razão social. Nulo mantém.", example = "Distribuidora Zomo ME")
    private String legalName;

    @Email
    @Size(max = 150)
    @Schema(description = "Novo e-mail de contato. Nulo mantém.", example = "compras@zomo.com.br")
    private String email;
}
