package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssociateTagRequest {
    @NotNull
    private Long tagId;
}
