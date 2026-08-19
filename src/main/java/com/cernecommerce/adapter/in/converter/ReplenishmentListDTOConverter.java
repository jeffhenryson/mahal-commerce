package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.ReplenishmentListItemResponseDTO;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;

public class ReplenishmentListDTOConverter {

    public ReplenishmentListItemResponseDTO toResponse(ReplenishmentListItem item) {
        ReplenishmentListItemResponseDTO dto = new ReplenishmentListItemResponseDTO();
        dto.setSku(item.sku());
        dto.setProductName(item.productNameSnapshot());
        dto.setCategory(item.categorySnapshot());
        dto.setBrand(item.brandSnapshot());
        dto.setUnit(item.unitSnapshot() == null ? null : item.unitSnapshot().name());
        dto.setCurrentStock(item.currentStockSnapshot());
        dto.setMinStock(item.minStockSnapshot());
        dto.setSuggestedQuantity(item.suggestedQuantitySnapshot());
        dto.setQuantity(item.quantity());
        dto.setUnitCost(item.unitCostSnapshot());
        if (item.previousPurchasedAtSnapshot() != null) {
            ReplenishmentListItemResponseDTO.PreviousPurchaseResponseDTO previousPurchase =
                    new ReplenishmentListItemResponseDTO.PreviousPurchaseResponseDTO();
            previousPurchase.setQuantity(item.previousPurchaseQuantitySnapshot());
            previousPurchase.setUnitCost(item.previousPurchaseUnitCostSnapshot());
            previousPurchase.setPurchasedAt(item.previousPurchasedAtSnapshot());
            dto.setPreviousPurchase(previousPurchase);
        }
        dto.setNote(item.note());
        dto.setCreatedAt(item.createdAt());
        dto.setCreatedBy(item.createdBy());
        return dto;
    }
}
