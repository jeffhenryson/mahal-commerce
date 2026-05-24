package com.security_spring.adapter.in.converter;

import com.security_spring.adapter.in.dtos.UserRequestDTO;
import com.security_spring.adapter.in.dtos.UserResponseDTO;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.domain.model.User;

import java.util.stream.Collectors;

public class UserDTOConverter {

    public User toDomain(UserRequestDTO dto) {
        User u = new User();
        u.setUsername(dto.getUsername());
        u.setPassword(dto.getPassword());
        return u;
    }

    public UserResponseDTO toResponse(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRoles(
            user.getRoles()
                .stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toList())
        );
        return dto;
    }
}
