package com.securityspring.adapter.in.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class UserResponseDTO {
    private Long id;
    private String username;
    private boolean enabled;
    private String email;
    private boolean emailVerified;
    private String pendingEmail;
    private List<String> roles;
    private List<String> permissions;
}
