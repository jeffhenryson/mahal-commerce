package com.securityspring.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securityspring.adapter.in.converter.RoleDTOConverter;
import com.securityspring.core.domain.exception.RoleAlreadyExistsException;
import com.securityspring.core.domain.exception.RoleNotFoundException;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.securityspring.core.domain.model.PageResult;

import java.util.List;

public class RoleControllerTest {

    private MockMvc mockMvc;
    private RoleUseCase roleUseCase;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        roleUseCase = mock(RoleUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoleController(roleUseCase, new RoleDTOConverter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returns_200_with_roles() throws Exception {
        Role r = new Role("ROLE_ADMIN");
        when(roleUseCase.listAll(0, 20))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void create_returns_201() throws Exception {
        Role created = new Role("ROLE_VIEWER");
        when(roleUseCase.createRole("ROLE_VIEWER")).thenReturn(created);

        mockMvc.perform(post("/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ROLE_VIEWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ROLE_VIEWER"));
    }

    @Test
    void create_duplicate_returns_409() throws Exception {
        when(roleUseCase.createRole("ROLE_ADMIN"))
                .thenThrow(new RoleAlreadyExistsException("ROLE_ADMIN"));

        mockMvc.perform(post("/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void delete_unknown_role_returns_404() throws Exception {
        doThrow(new RoleNotFoundException("ROLE_GHOST"))
                .when(roleUseCase).deleteRole("ROLE_GHOST");

        mockMvc.perform(delete("/roles/ROLE_GHOST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns_204() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_VIEWER"))
                .andExpect(status().isNoContent());
        verify(roleUseCase).deleteRole("ROLE_VIEWER");
    }
}
