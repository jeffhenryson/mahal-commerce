package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.core.domain.model.estoque.MovementType;

public class StockMovementDTOConverter {

    public MovementType toType(String type) {
        return MovementType.valueOf(type);
    }
}
