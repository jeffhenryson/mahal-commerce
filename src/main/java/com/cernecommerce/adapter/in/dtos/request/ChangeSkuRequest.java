package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Corpo da troca de SKU (EST-F030). Endpoint próprio, não um campo do PATCH de edição: trocar o
 * SKU reescreve saldo, lotes e todo o histórico de vendas e compras do item, e merece permissão
 * explícita e evento de auditoria próprio — não pode acontecer de carona numa correção de nome.
 *
 * <p>Mesmos limites do {@code sku} de {@link ProductRequest}.</p>
 */
@Data
public class ChangeSkuRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String newSku;
}
