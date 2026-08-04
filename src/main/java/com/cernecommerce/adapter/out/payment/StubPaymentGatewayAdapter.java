package com.cernecommerce.adapter.out.payment;

import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter padrão de {@code dev}/testes (ECM-F004) — nenhum ambiente precisa de credenciais reais
 * do InfinitePay para compilar, subir ou rodar a suíte. Mesma função de {@code LoggingEmailAdapter}
 * para o e-mail: um substituto inócuo, não um mock de teste.
 *
 * <p>{@link #checkPayment} confirma como pago o valor pedido em {@link #createCheckoutLink} para
 * o mesmo {@code orderNsu} (guardado em memória) — não um valor fixo arbitrário — para exercitar de
 * verdade a checagem de valor do webhook localmente, sem um gateway real. {@code orderNsu} nunca
 * visto antes devolve não-pago.</p>
 */
public class StubPaymentGatewayAdapter implements PaymentGatewayPort {

    private final Map<String, BigDecimal> requestedAmountsByOrderNsu = new ConcurrentHashMap<>();

    @Override
    public CheckoutLink createCheckoutLink(String orderNsu, BigDecimal amount, String itemDescription,
            String customerName, String customerEmail) {
        requestedAmountsByOrderNsu.put(orderNsu, amount);
        return new CheckoutLink("https://stub.invalid/checkout/" + UUID.randomUUID());
    }

    @Override
    public PaymentCheckResult checkPayment(String orderNsu, String transactionNsu, String invoiceSlug) {
        BigDecimal amount = requestedAmountsByOrderNsu.get(orderNsu);
        return amount == null ? new PaymentCheckResult(false, null) : new PaymentCheckResult(true, amount);
    }
}
