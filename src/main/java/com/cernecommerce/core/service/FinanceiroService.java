package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;

import java.util.List;

/**
 * Implementação stub do {@link FinanceiroUseCase}.
 *
 * <p>Classe pura conectada via {@code @Bean} em {@code CoreBeanConfig}. Quando o
 * adapter de persistência existir, injetar {@code LedgerRepository} pelo construtor
 * (ver {@code core.ports.out.financeiro}).</p>
 */
public class FinanceiroService implements FinanceiroUseCase {

    // TODO: injetar LedgerRepository (core.ports.out.financeiro) quando o adapter existir.

    @Override
    public List<CashFlowEntry> listCashFlow() {
        // TODO: delegar ao LedgerRepository.findAll().
        return List.of();
    }
}
