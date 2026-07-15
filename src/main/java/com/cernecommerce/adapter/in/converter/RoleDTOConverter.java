package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.RoleResponseDTO;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.domain.model.rbac.Role;

import java.util.stream.Collectors;

public class RoleDTOConverter {

    public RoleResponseDTO toResponse(Role role) {
        RoleResponseDTO dto = new RoleResponseDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setPermissions(role.getPermissions().stream()
                .map(Permission::getName)
                .sorted()
                .collect(Collectors.toList()));
        return dto;
    }
}
