package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.compras.GoodsReceiptRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class ComprasService implements ComprasUseCase {

    private final SupplierRepository supplierRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final EstoqueUseCase estoqueUseCase;

    public ComprasService(SupplierRepository supplierRepository, GoodsReceiptRepository goodsReceiptRepository,
            EstoqueUseCase estoqueUseCase) {
        this.supplierRepository = supplierRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.estoqueUseCase = estoqueUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Supplier> listSuppliers(int page, int size) {
        return supplierRepository.findAll(page, size);
    }

    @Override
    @Transactional
    public GoodsReceipt receiveGoods(Long supplierId, String warehouseCode, List<GoodsReceiptItem> items,
            String username) {
        supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));

        for (GoodsReceiptItem item : items) {
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.ENTRADA, item.quantity(),
                    "Recebimento de mercadoria - fornecedor #" + supplierId, username);
        }

        GoodsReceipt receipt = GoodsReceipt.create(supplierId, warehouseCode, items, username);
        return goodsReceiptRepository.save(receipt);
    }
}
