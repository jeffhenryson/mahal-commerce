package com.securityspring.adapter.in.converter;

import com.securityspring.adapter.in.dtos.response.RoleResponseDTO;
import com.securityspring.core.domain.model.rbac.Permission;
import com.securityspring.core.domain.model.rbac.Role;

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
