package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.PermissionResponseDTO;
import com.cernecommerce.core.domain.model.rbac.Permission;

public class PermissionDTOConverter {

    public PermissionResponseDTO toResponse(Permission permission) {
        PermissionResponseDTO dto = new PermissionResponseDTO();
        dto.setId(permission.getId());
        dto.setName(permission.getName());
        return dto;
    }
}
