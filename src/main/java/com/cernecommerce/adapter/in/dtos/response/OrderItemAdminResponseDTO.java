package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item de pedido na visão do administrador.
 *
 * <p>Difere de {@link OrderItemResponseDTO} por expor <b>custo e margem</b>. Não é duplicação
 * gratuita: {@code PDV_READ} é a permissão mais distribuída do módulo, e o operador de caixa não
 * precisa — nem deve — ver quanto a loja ganha em cada item.</p>
 */
@Data
public class OrderItemAdminResponseDTO {

    private Long id;
    private String sku;

    @Schema(description = "Nome do produto, congelado no instante da venda — não muda se o produto "
            + "for renomeado depois. Nulo em pedidos anteriores a esta coluna.")
    private String productName;

    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;

    @Schema(description = "Custo unitário congelado na venda. Nulo em pedidos anteriores à V65 — "
            + "um zero aqui mentiria sobre a margem.")
    private BigDecimal costPrice;

    @Schema(description = "Lucro bruto do item: líquido − custo × quantidade. Negativo quando se "
            + "vendeu abaixo do custo, o que é permitido e apenas sinalizado.")
    private BigDecimal marginAmount;

    private BigDecimal cashbackPercent;
    private BigDecimal cashbackAmount;
}
