package com.security_spring.adapter.in.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.security_spring.adapter.in.converter.UserDTOConverter;
import com.security_spring.adapter.in.dtos.UserRequestDTO;
import com.security_spring.adapter.in.dtos.UserResponseDTO;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.in.UserUseCase;

import java.net.URI;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserUseCase useCase;
    private final UserDTOConverter converter;

    public UserController(UserUseCase useCase, UserDTOConverter converter) {
        this.useCase = useCase;
        this.converter = converter;
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO request) {
        User created = useCase.createUser(request.getUsername(), request.getPassword());
        UserResponseDTO body = converter.toResponse(created);
        return ResponseEntity.created(URI.create("/users/" + body.getId())).body(body);
    }

    @PostMapping("/{username}/roles/{roleName}")
    public ResponseEntity<Void> assignRole(@PathVariable String username, @PathVariable String roleName) {
        useCase.assignRole(username, roleName);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(useCase.getUserById(id)));
    }

    @GetMapping
    public ResponseEntity<java.util.List<UserResponseDTO>> list() {
        return ResponseEntity.ok(
            useCase.listAll().stream()
                .map(converter::toResponse)
                .collect(Collectors.toList())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        useCase.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

