package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cashback ganho na conclusão da venda de balcão, de ponta a ponta contra banco real (CRM-F003).
 *
 * <p>Usa a taxa GLOBAL semeada pela V70 (3%) — não cria uma segunda taxa GLOBAL no teste porque
 * {@code CashbackService.createRate} recusaria por já existir uma ativa e em aberto, exatamente a
 * checagem que se quer preservar.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CashbackFlowIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired CashbackUseCase cashbackUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** cpf é VARCHAR(11) — precisa de um valor numérico, não do sufixo alfanumérico usado alhures. */
    private String uniqueCpf() {
        return String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
    }

    private static List<PaymentCommand> cash(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private String givenStockedWarehouse(String operator, String suffix) {
        String warehouseCode = "LOJA-" + suffix;
        String sku = "ESSENCIA-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);
        return warehouseCode + "|" + sku;
    }

    @Test
    void registerSale_earnsCashbackForAnOfficiallyRegisteredCustomer() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator, suffix).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        Customer customer = crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                uniqueCpf(), "balcao");
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(session.id(), customer.id(),
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("100.00"), operator);
        flushAndClear();

        // 100,00 líquido x 3% (taxa GLOBAL da V70) = 3,00, ainda em carência.
        CashbackBalance balance = cashbackUseCase.getCustomerBalance(customer.id());
        assertThat(balance.available()).isEqualByComparingTo("0.00");
        assertThat(balance.pending()).isEqualByComparingTo("3.00");

        List<CashbackEntry> entries = cashbackUseCase.listCustomerEntries(customer.id(), 0, 10).content();
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(CashbackEntryType.EARNED);
            assertThat(entry.orderId()).isEqualTo(order.id());
            assertThat(entry.amount()).isEqualByComparingTo("3.00");
            assertThat(entry.availableAt()).isAfter(order.paidAt());
        });
    }

    @Test
    void registerSale_doesNotEarnCashbackForACustomerWithoutCpf() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator, suffix).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        // Cliente "leve": só contato, sem CPF — não é elegível a cashback (Customer.isOfficiallyRegistered()).
        Customer customer = crmUseCase.createCustomer("Cliente Leve " + suffix, "1198" + suffix, null, null, "balcao");
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        pdvUseCase.registerSale(session.id(), customer.id(),
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("100.00"), operator);
        flushAndClear();

        CashbackBalance balance = cashbackUseCase.getCustomerBalance(customer.id());
        assertThat(balance.available()).isEqualByComparingTo("0.00");
        assertThat(balance.pending()).isEqualByComparingTo("0.00");
        assertThat(cashbackUseCase.listCustomerEntries(customer.id(), 0, 10).content()).isEmpty();
    }

    @Test
    void registerSale_doesNotEarnCashbackForAnAnonymousSale() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator, suffix).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("100.00"), operator);
        flushAndClear();

        assertThat(order.customerId()).isNull();
        // Sem cliente não há para quem consultar o saldo — a prova aqui é que a venda conclui
        // normalmente, sem exceção, mesmo com a taxa GLOBAL resolvendo para o item.
        assertThat(order.status().name()).isEqualTo("CONCLUIDO");
    }
}
