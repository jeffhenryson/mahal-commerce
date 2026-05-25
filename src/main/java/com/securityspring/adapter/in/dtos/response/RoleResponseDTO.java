package com.securityspring.adapter.in.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class RoleResponseDTO {
    private Long id;
    private String name;
    private List<String> permissions;
}
