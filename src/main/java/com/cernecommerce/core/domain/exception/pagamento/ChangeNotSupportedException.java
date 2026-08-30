package com.cernecommerce.core.domain.exception.pagamento;

import java.math.BigDecimal;

/**
 * O pagamento excede o total, mas este pedido não tem onde guardar troco (PDV-C015).
 *
 * <p>É o caso da liquidação no balcão de um pedido montado no app: o canal permanece
 * {@code MARKETPLACE} — foi o site que gerou a venda — e {@code Order} recusa
 * {@code changeAmount} positivo nesse canal ({@code ck_sales_order_change_amount_by_channel}).
 * Aceitar o excedente sem gravá-lo faria a linha de pagamento dizer que entrou mais dinheiro do
 * que a gaveta ficou com, que é precisamente o defeito que PDV-C017 acabou de tirar do
 * fechamento.</p>
 *
 * <p>Por isso a liquidação exige <b>valor exato</b>: o operador lança o que fica na gaveta e
 * devolve a diferença por fora do sistema, como já faz com qualquer conferência de gaveta. Se um
 * dia valer a pena registrar o entregue aqui também, o caminho é estender o canal no
 * {@code CHECK} — não afrouxar esta checagem.</p>
 */
public class ChangeNotSupportedException extends RuntimeException {

    public ChangeNotSupportedException(BigDecimal totalPaid, BigDecimal netAmount) {
        super("Pedido do aplicativo tem que ser liquidado pelo valor exato: pago " + totalPaid
                + ", líquido do pedido " + netAmount + ". Lance o valor que fica na gaveta");
    }
}
