package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.adapter.in.converter.RoleDTOConverter;
import com.cernecommerce.core.domain.exception.RoleAlreadyExistsException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cernecommerce.core.domain.model.PageResult;

import java.util.List;

public class RoleControllerTest {

    private MockMvc mockMvc;
    private RoleUseCase roleUseCase;
    private final ObjectMapper om = new ObjectMapper();

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        roleUseCase = mock(RoleUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SystemConfigUseCase systemConfig = mock(SystemConfigUseCase.class);
        when(systemConfig.getBoolean("module.roles.enabled", true)).thenReturn(true);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoleController(roleUseCase, new RoleDTOConverter(), publisher, systemConfig))
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
                .principal(AUTH)
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
        mockMvc.perform(delete("/roles/ROLE_VIEWER").principal(AUTH))
                .andExpect(status().isNoContent());
        verify(roleUseCase).deleteRole("ROLE_VIEWER");
    }

    // ── getByName ─────────────────────────────────────────────────────────────

    @Test
    void getByName_returns200_with_role() throws Exception {
        Role r = new Role("ROLE_VIEWER");
        when(roleUseCase.findByName("ROLE_VIEWER")).thenReturn(r);

        mockMvc.perform(get("/roles/ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ROLE_VIEWER"));
    }

    @Test
    void getByName_returns404_when_not_found() throws Exception {
        when(roleUseCase.findByName("ROLE_GHOST"))
                .thenThrow(new RoleNotFoundException("ROLE_GHOST"));

        mockMvc.perform(get("/roles/ROLE_GHOST"))
                .andExpect(status().isNotFound());
    }

    // ── list with search ──────────────────────────────────────────────────────

    @Test
    void list_with_search_delegates_to_findByNameContaining() throws Exception {
        when(roleUseCase.findByNameContaining("ADMIN", 0, 20))
                .thenReturn(new PageResult<>(List.of(new Role("ROLE_ADMIN")), 0, 20, 1L, 1));

        mockMvc.perform(get("/roles").param("search", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("ROLE_ADMIN"));

        verify(roleUseCase).findByNameContaining("ADMIN", 0, 20);
        verify(roleUseCase, never()).listAll(anyInt(), anyInt());
    }

    // ── assignPermission ──────────────────────────────────────────────────────

    @Test
    void assignPermission_returns204() throws Exception {
        mockMvc.perform(post("/roles/ROLE_ADMIN/permissions/USER_READ").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(roleUseCase).assignPermission("ROLE_ADMIN", "USER_READ");
    }

    @Test
    void assignPermission_role_not_found_returns404() throws Exception {
        doThrow(new RoleNotFoundException("ROLE_GHOST"))
                .when(roleUseCase).assignPermission("ROLE_GHOST", "USER_READ");

        mockMvc.perform(post("/roles/ROLE_GHOST/permissions/USER_READ").principal(AUTH))
                .andExpect(status().isNotFound());
    }

    // ── removePermission ──────────────────────────────────────────────────────

    @Test
    void removePermission_returns204() throws Exception {
        mockMvc.perform(delete("/roles/ROLE_ADMIN/permissions/USER_READ").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(roleUseCase).removePermission("ROLE_ADMIN", "USER_READ");
    }
}
