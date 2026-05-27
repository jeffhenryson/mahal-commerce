package com.securityspring.adapter.in.converter;

import com.securityspring.adapter.in.dtos.response.PermissionResponseDTO;
import com.securityspring.core.domain.model.rbac.Permission;

public class PermissionDTOConverter {

    public PermissionResponseDTO toResponse(Permission permission) {
        PermissionResponseDTO dto = new PermissionResponseDTO();
        dto.setId(permission.getId());
        dto.setName(permission.getName());
        return dto;
    }
}
