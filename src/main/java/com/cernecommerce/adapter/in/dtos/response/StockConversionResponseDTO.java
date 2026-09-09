package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

/**
 * Resultado de uma conversão entre SKUs (EST-F025): os dois saldos já atualizados.
 *
 * <p>Devolve as duas pontas porque é a pergunta que o operador faz assim que converte — quanto
 * sobrou da origem e quanto passou a existir do destino. Devolver só uma delas obrigaria a tela a um
 * {@code GET /estoque/stock-balance} extra para completar a informação que a operação já tinha na
 * mão.</p>
 */
@Data
public class StockConversionResponseDTO {

    /** Saldo do SKU de origem depois da saída. */
    private StockBalanceResponseDTO from;

    /** Saldo do SKU de destino depois da entrada. */
    private StockBalanceResponseDTO to;
}
