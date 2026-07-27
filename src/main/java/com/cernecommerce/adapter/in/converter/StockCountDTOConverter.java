package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.StockCountItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockCountResponseDTO;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;

public class StockCountDTOConverter {

    /**
     * O {@code warehouseCode} vem de fora porque o domínio guarda só o {@code warehouseId} — é o
     * mesmo arranjo de {@code StockMovementDTOConverter.toResponse}.
     */
    public StockCountResponseDTO toResponse(StockCount count, String warehouseCode) {
        StockCountResponseDTO dto = new StockCountResponseDTO();
        dto.setId(count.id());
        dto.setWarehouseCode(warehouseCode);
        dto.setStatus(count.status().name());
        dto.setUsername(count.username());
        dto.setCreatedAt(count.createdAt());
        dto.setClosedAt(count.closedAt());
        dto.setItems(count.items().stream().map(this::toResponse).toList());
        return dto;
    }

    public StockCountItemResponseDTO toResponse(StockCountItem item) {
        StockCountItemResponseDTO dto = new StockCountItemResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setCountedQuantity(item.countedQuantity());
        dto.setExpectedQuantity(item.expectedQuantity());
        dto.setDifference(item.difference());
        return dto;
    }
}
