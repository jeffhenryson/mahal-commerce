package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Sale;
import com.cernecommerce.core.domain.model.pdv.SaleItem;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.SaleRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class PdvService implements PdvUseCase {

    private final CashRegisterRepository cashRegisterRepository;
    private final SaleRepository saleRepository;
    private final EstoqueUseCase estoqueUseCase;

    public PdvService(CashRegisterRepository cashRegisterRepository, SaleRepository saleRepository,
            EstoqueUseCase estoqueUseCase) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.saleRepository = saleRepository;
        this.estoqueUseCase = estoqueUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> listSessions(int page, int size) {
        return cashRegisterRepository.findAll(page, size);
    }

    @Override
    @Transactional
    public Sale registerSale(Long sessionId, String warehouseCode, List<SaleItem> items, String username) {
        CashRegisterSession session = cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
        if (session.status() != CashRegisterSession.Status.OPEN) {
            throw new CashRegisterSessionClosedException(sessionId);
        }

        for (SaleItem item : items) {
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.SAIDA, item.quantity(),
                    "Venda balcão sessão #" + sessionId, username);
        }

        Sale sale = Sale.create(sessionId, warehouseCode, items);
        return saleRepository.save(sale);
    }
}
