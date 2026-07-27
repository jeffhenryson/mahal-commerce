package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Sale;
import com.cernecommerce.core.domain.model.pdv.SaleItem;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdvServiceTest {

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock SaleRepository saleRepository;
    @Mock EstoqueUseCase estoqueUseCase;

    PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService(cashRegisterRepository, saleRepository, estoqueUseCase);
    }

    private CashRegisterSession openSession() {
        return new CashRegisterSession(1L, "gerente", Instant.now(), BigDecimal.TEN, null,
                CashRegisterSession.Status.OPEN);
    }

    @Test
    void listSessions_delegatesToRepository() {
        PageResult<CashRegisterSession> page = new PageResult<>(List.of(openSession()), 0, 20, 1L, 1);
        when(cashRegisterRepository.findAll(0, 20)).thenReturn(page);

        PageResult<CashRegisterSession> result = pdvService.listSessions(0, 20);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void registerSale_adjustsStockPerItemAndSavesSale() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        StockBalance balance = StockBalance.of(1L, "NARG-001", 2L, new BigDecimal("18.000"), 1L);
        when(estoqueUseCase.adjustStock(eq("NARG-001"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("2.000")), any(), eq("caixa1"))).thenReturn(balance);
        when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<SaleItem> items = List.of(new SaleItem("NARG-001", new BigDecimal("2.000"), new BigDecimal("15.00")));
        Sale result = pdvService.registerSale(1L, "LOJA-01", items, "caixa1");

        assertThat(result.sessionId()).isEqualTo(1L);
        assertThat(result.totalAmount()).isEqualByComparingTo("30.00");
        verify(estoqueUseCase).adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("2.000"),
                "Venda balcão sessão #1", "caixa1");
        verify(saleRepository).save(any());
    }

    @Test
    void registerSale_throwsWhenSessionNotFound() {
        when(cashRegisterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.registerSale(99L, "LOJA-01",
                List.of(new SaleItem("NARG-001", BigDecimal.ONE, BigDecimal.TEN)), "caixa1"))
                .isInstanceOf(CashRegisterSessionNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void registerSale_throwsWhenSessionIsClosed() {
        CashRegisterSession closed = new CashRegisterSession(1L, "gerente", Instant.now(), BigDecimal.TEN,
                Instant.now(), CashRegisterSession.Status.CLOSED);
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> pdvService.registerSale(1L, "LOJA-01",
                List.of(new SaleItem("NARG-001", BigDecimal.ONE, BigDecimal.TEN)), "caixa1"))
                .isInstanceOf(CashRegisterSessionClosedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void registerSale_propagatesUnknownSkuAndDoesNotSaveSale() {
        // EST-C002: antes, um SKU digitado errado no PDV criava saldo e ledger órfãos e a venda
        // era gravada normalmente. Agora a venda inteira é revertida.
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        List<SaleItem> items = List.of(new SaleItem("SKU-FANTASMA", BigDecimal.ONE, BigDecimal.TEN));

        assertThatThrownBy(() -> pdvService.registerSale(1L, "LOJA-01", items, "caixa1"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(saleRepository, never()).save(any());
    }

    @Test
    void registerSale_propagatesInsufficientStockAndDoesNotSaveSale() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientStockException("NARG-001", 2L, new BigDecimal("1.000"),
                        new BigDecimal("5.000")));

        List<SaleItem> items = List.of(new SaleItem("NARG-001", new BigDecimal("5.000"), BigDecimal.TEN));

        assertThatThrownBy(() -> pdvService.registerSale(1L, "LOJA-01", items, "caixa1"))
                .isInstanceOf(InsufficientStockException.class);

        verify(saleRepository, never()).save(any());
    }
}
