package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

@Data
public class WarehouseResponseDTO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private boolean active;
}
