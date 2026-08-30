package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fechamento de comanda. Mesmo shape de pagamento de {@code SaleRequest} (PDV-F006): pelo menos
 * uma linha, várias linhas = pagamento dividido.
 */
@Data
public class CloseComandaRequest {

    @NotEmpty
    @Valid
    @Schema(description = "Pelo menos uma linha. Várias linhas = pagamento dividido.")
    private List<SalePaymentRequest> payments;

    @DecimalMin(value = "0.0")
    @Schema(description = "PDV-F014 — abatimento em valor absoluto sobre a CONTA INTEIRA (o "
            + "desconto de fim de noite), não sobre um item. O servidor rateia entre as linhas "
            + "proporcionalmente ao valor de cada uma, para o cashback e a margem continuarem "
            + "certos. Exige PDV_COMANDA_DISCOUNT e respeita o mesmo teto do balcão "
            + "(pdv.sale.max-discount-percent): acima dele, 409 DISCOUNT_LIMIT_EXCEEDED. "
            + "Não confundir com surchargeAmount, que é acréscimo por linha no open rosh.",
            example = "20.00")
    private BigDecimal discountAmount;

    @Schema(description = "PDV-F015 — taxa de serviço (os 10% do garçom). Vem APLICADA POR PADRÃO, "
            + "porque é o padrão do salão: mandar false é o cliente recusando. O valor é calculado "
            + "pelo servidor como percentual sobre o líquido (portanto depois do desconto) e "
            + "gravado em campo próprio, fora do netAmount — o líquido é receita da casa, a taxa é "
            + "repasse. Consulte o percentual vigente em GET /pdv/comandas/service-fee.",
            defaultValue = "true")
    private Boolean applyServiceFee;

    /** Ausente é "sim": a taxa é o padrão do salão, e omitir não pode significar deixar de cobrar. */
    public boolean isServiceFeeApplied() {
        return applyServiceFee == null || applyServiceFee;
    }
}
