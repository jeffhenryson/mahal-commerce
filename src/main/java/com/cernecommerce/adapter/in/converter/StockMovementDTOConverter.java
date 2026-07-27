package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.StockMovementResponseDTO;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockMovement;

public class StockMovementDTOConverter {

    public MovementType toType(String type) {
        return MovementType.valueOf(type);
    }

    public StockMovementResponseDTO toResponse(StockMovement movement, String warehouseCode) {
        StockMovementResponseDTO dto = new StockMovementResponseDTO();
        dto.setId(movement.id());
        dto.setSku(movement.sku());
        dto.setWarehouseCode(warehouseCode);
        dto.setType(movement.type().name());
        dto.setQuantity(movement.quantity());
        dto.setReason(movement.reason());
        dto.setUsername(movement.username());
        dto.setCreatedAt(movement.createdAt());
        return dto;
    }
}
