package com.securityspring.adapter.in.converter;

import com.securityspring.adapter.in.dtos.response.UserResponseDTO;
import com.securityspring.core.domain.model.Permission;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.domain.model.User;

import java.util.stream.Collectors;

public class UserDTOConverter {

    public UserResponseDTO toResponse(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEnabled(user.isEnabled());
        dto.setEmail(user.getEmail());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setRoles(
            user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toList())
        );
        dto.setPermissions(
            user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList())
        );
        return dto;
    }
}
