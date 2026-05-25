package com.security_spring.adapter.in.converter;

import com.security_spring.adapter.in.dtos.request.UserRequestDTO;
import com.security_spring.adapter.in.dtos.response.UserResponseDTO;
import com.security_spring.core.domain.model.Permission;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;

import java.util.stream.Collectors;

public class UserDTOConverter {

    public User toDomain(UserRequestDTO dto) {
        User u = User.of(dto.getUsername(), dto.getPassword(), null);
        return u;
    }

    public UserResponseDTO toResponse(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEnabled(user.isEnabled());
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
