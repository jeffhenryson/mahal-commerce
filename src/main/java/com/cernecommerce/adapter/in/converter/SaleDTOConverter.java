package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.SaleItemRequest;
import com.cernecommerce.adapter.in.dtos.response.SaleItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleResponseDTO;
import com.cernecommerce.core.domain.model.pdv.Sale;
import com.cernecommerce.core.domain.model.pdv.SaleItem;

import java.util.List;

public class SaleDTOConverter {

    public List<SaleItem> toItems(List<SaleItemRequest> requests) {
        return requests.stream()
                .map(r -> new SaleItem(r.getSku(), r.getQuantity(), r.getUnitPrice()))
                .toList();
    }

    public SaleResponseDTO toResponse(Sale sale) {
        SaleResponseDTO dto = new SaleResponseDTO();
        dto.setId(sale.id());
        dto.setSessionId(sale.sessionId());
        dto.setWarehouseCode(sale.warehouseCode());
        dto.setSoldAt(sale.soldAt());
        dto.setTotalAmount(sale.totalAmount());
        dto.setItems(sale.items().stream().map(this::toResponse).toList());
        return dto;
    }

    private SaleItemResponseDTO toResponse(SaleItem item) {
        SaleItemResponseDTO dto = new SaleItemResponseDTO();
        dto.setSku(item.sku());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        return dto;
    }
}
