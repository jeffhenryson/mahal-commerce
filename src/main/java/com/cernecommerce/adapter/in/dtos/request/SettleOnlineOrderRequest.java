package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Liquidação no balcão de um pedido montado no aplicativo (PDV-C015). Mesmo shape de pagamento de
 * {@code SaleRequest} e {@code CloseComandaRequest}: pelo menos uma linha, várias linhas =
 * pagamento dividido.
 *
 * <p>A rota não tinha corpo até PDV-C015 — o pedido era concluído sem registrar como o dinheiro
 * entrou, e o fechamento daquele caixa acusava sobra sem dono.</p>
 */
@Data
public class SettleOnlineOrderRequest {

    @NotEmpty
    @Valid
    @Schema(description = "Pelo menos uma linha. A soma tem que bater EXATAMENTE com o líquido do "
            + "pedido: o canal continua MARKETPLACE, e pedido de marketplace não tem onde guardar "
            + "troco. Lance o valor que fica na gaveta.")
    private List<SalePaymentRequest> payments;
}
