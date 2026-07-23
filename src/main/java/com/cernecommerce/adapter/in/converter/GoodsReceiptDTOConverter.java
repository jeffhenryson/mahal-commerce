package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.GoodsReceiptItemRequest;
import com.cernecommerce.adapter.in.dtos.response.GoodsReceiptItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.GoodsReceiptResponseDTO;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;

import java.util.List;

public class GoodsReceiptDTOConverter {

    public List<GoodsReceiptItem> toItems(List<GoodsReceiptItemRequest> requests) {
        return requests.stream()
                .map(r -> new GoodsReceiptItem(r.getSku(), r.getQuantity()))
                .toList();
    }

    public GoodsReceiptResponseDTO toResponse(GoodsReceipt receipt) {
        GoodsReceiptResponseDTO dto = new GoodsReceiptResponseDTO();
        dto.setId(receipt.id());
        dto.setSupplierId(receipt.supplierId());
        dto.setWarehouseCode(receipt.warehouseCode());
        dto.setUsername(receipt.username());
        dto.setReceivedAt(receipt.receivedAt());
        dto.setItems(receipt.items().stream().map(this::toResponse).toList());
        return dto;
    }

    private GoodsReceiptItemResponseDTO toResponse(GoodsReceiptItem item) {
        GoodsReceiptItemResponseDTO dto = new GoodsReceiptItemResponseDTO();
        dto.setSku(item.sku());
        dto.setQuantity(item.quantity());
        return dto;
    }
}
