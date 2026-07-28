package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusRequest {

    @NotNull
    @Schema(description = "Novo estado. A transição precisa ser permitida pela máquina de estados — "
            + "consulte allowedTransitions no detalhe do pedido.", example = "SEPARADO")
    private OrderStatus status;
}
