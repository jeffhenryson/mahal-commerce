package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.OrphanSkuResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockMovementResponseDTO;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
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

    /**
     * Diferente de {@link #toResponse(StockMovement, String)}, aqui o {@code warehouseCode} vem
     * do próprio domínio: o diagnóstico de integridade varre todos os depósitos de uma vez, então
     * não há um código informado na consulta para reaproveitar.
     */
    public OrphanSkuResponseDTO toResponse(OrphanSku orphan) {
        OrphanSkuResponseDTO dto = new OrphanSkuResponseDTO();
        dto.setSku(orphan.sku());
        dto.setWarehouseCode(orphan.warehouseCode());
        dto.setQuantity(orphan.quantity());
        dto.setMovementCount(orphan.movementCount());
        dto.setHasReorderPoint(orphan.hasReorderPoint());
        dto.setLastMovementAt(orphan.lastMovementAt());
        return dto;
    }
}
