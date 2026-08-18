package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.ComandaItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;

import java.util.List;

public class ComandaDTOConverter {

    public ComandaResponseDTO toResponse(Comanda comanda) {
        ComandaResponseDTO dto = new ComandaResponseDTO();
        dto.setId(comanda.id());
        dto.setSessionId(comanda.sessionId());
        dto.setWarehouseCode(comanda.warehouseCode());
        dto.setTableOrCustomerLabel(comanda.tableOrCustomerLabel());
        dto.setStatus(comanda.status().name());
        dto.setItems(comanda.items().stream().map(this::toResponse).toList());
        dto.setRunningTotal(comanda.runningTotal());
        dto.setOrderId(comanda.orderId());
        dto.setOpenedBy(comanda.openedBy());
        dto.setOpenedAt(comanda.openedAt());
        dto.setClosedAt(comanda.closedAt());
        return dto;
    }

    public List<ComandaResponseDTO> toResponse(List<Comanda> comandas) {
        return comandas.stream().map(this::toResponse).toList();
    }

    private ComandaItemResponseDTO toResponse(ComandaItem item) {
        ComandaItemResponseDTO dto = new ComandaItemResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setCostPrice(item.costPrice());
        dto.setSubtotal(item.subtotal());
        dto.setAddedAt(item.addedAt());
        return dto;
    }
}
