package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.StockBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WarehouseResponseDTO;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;

public class WarehouseDTOConverter {

    public WarehouseType toType(String type) {
        return WarehouseType.valueOf(type);
    }

    /**
     * Variante para o PATCH (EST-F018), onde ausência do campo significa "não mexer": {@code null}
     * atravessa em vez de estourar {@link NullPointerException} em {@code valueOf}.
     */
    public WarehouseType toTypeOrNull(String type) {
        return type == null ? null : WarehouseType.valueOf(type);
    }

    public WarehouseResponseDTO toResponse(Warehouse warehouse) {
        WarehouseResponseDTO dto = new WarehouseResponseDTO();
        dto.setId(warehouse.id());
        dto.setCode(warehouse.code());
        dto.setName(warehouse.name());
        dto.setType(warehouse.type().name());
        dto.setActive(warehouse.active());
        return dto;
    }

    public StockBalanceResponseDTO toResponse(StockBalance balance, String warehouseCode) {
        StockBalanceResponseDTO dto = new StockBalanceResponseDTO();
        dto.setSku(balance.sku());
        dto.setWarehouseCode(warehouseCode);
        dto.setQuantity(balance.quantity());
        return dto;
    }
}
