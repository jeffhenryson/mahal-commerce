package com.cernecommerce.adapter.out.payment;

import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort.CheckoutLink;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort.PaymentCheckResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class StubPaymentGatewayAdapterTest {

    private final StubPaymentGatewayAdapter adapter = new StubPaymentGatewayAdapter();

    @Test
    void createCheckoutLink_returnsLinkAndStoresAmount() {
        CheckoutLink link = adapter.createCheckoutLink("nsu123", new BigDecimal("100.50"), "Items", "John", "john@example.com");
        
        assertThat(link.checkoutUrl()).startsWith("https://stub.invalid/checkout/");
        
        PaymentCheckResult result = adapter.checkPayment("nsu123", "txn456", "inv789");
        assertThat(result.paid()).isTrue();
        assertThat(result.paidAmount()).isEqualTo(new BigDecimal("100.50"));
    }

    @Test
    void checkPayment_returnsFalseForUnknownNsu() {
        PaymentCheckResult result = adapter.checkPayment("unknown", "txn456", "inv789");
        
        assertThat(result.paid()).isFalse();
        assertThat(result.paidAmount()).isNull();
    }
}
