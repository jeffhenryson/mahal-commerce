package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.PdvUseCase;

import java.util.List;

/**
 * Implementação stub do {@link PdvUseCase}.
 *
 * <p>Classe pura (sem Spring além de {@code @Transactional} quando necessário),
 * conectada via {@code @Bean} em {@code CoreBeanConfig}. Quando o adapter de
 * persistência existir, injetar {@code CashRegisterRepository} pelo construtor
 * (ver {@code core.ports.out.pdv}).</p>
 */
public class PdvService implements PdvUseCase {

    // TODO: injetar CashRegisterRepository (core.ports.out.pdv) quando o adapter existir.

    @Override
    public PageResult<CashRegisterSession> listSessions(int page, int size) {
        // TODO: delegar ao CashRegisterRepository.findAll(page, size).
        return new PageResult<>(List.of(), page, size, 0, 0);
    }
}
