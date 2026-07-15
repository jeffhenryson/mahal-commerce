package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.PermissionDTOConverter;
import com.cernecommerce.core.domain.exception.PermissionNotFoundException;
import com.cernecommerce.core.domain.exception.PermissionAlreadyExistsException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

public class PermissionControllerTest {

    private MockMvc mockMvc;
    private PermissionUseCase permissionUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        permissionUseCase = mock(PermissionUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SystemConfigUseCase systemConfig = mock(SystemConfigUseCase.class);
        when(systemConfig.getBoolean("module.roles.enabled", true)).thenReturn(true);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PermissionController(permissionUseCase, new PermissionDTOConverter(), publisher, systemConfig))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Permission perm(String name) {
        return Permission.of(1L, name);
    }

    @Test
    void list_returns_200_with_permissions() throws Exception {
        when(permissionUseCase.listAll(0, 20))
                .thenReturn(new PageResult<>(List.of(perm("USER_READ"), perm("USER_CREATE")), 0, 20, 2L, 1));

        mockMvc.perform(get("/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("USER_READ"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void get_by_name_returns_200() throws Exception {
        when(permissionUseCase.findByName("USER_READ")).thenReturn(perm("USER_READ"));

        mockMvc.perform(get("/permissions/USER_READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("USER_READ"));
    }

    @Test
    void get_by_name_not_found_returns_404() throws Exception {
        when(permissionUseCase.findByName("GHOST"))
                .thenThrow(new PermissionNotFoundException("GHOST"));

        mockMvc.perform(get("/permissions/GHOST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns_201() throws Exception {
        Permission created = perm("REPORT_READ");
        when(permissionUseCase.createPermission("REPORT_READ")).thenReturn(created);

        mockMvc.perform(post("/permissions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"REPORT_READ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("REPORT_READ"));
    }

    @Test
    void create_duplicate_returns_409() throws Exception {
        when(permissionUseCase.createPermission("USER_READ"))
                .thenThrow(new PermissionAlreadyExistsException("USER_READ"));

        mockMvc.perform(post("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"USER_READ\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void delete_returns_204() throws Exception {
        mockMvc.perform(delete("/permissions/USER_READ").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(permissionUseCase).deletePermission("USER_READ");
    }

    @Test
    void delete_not_found_returns_404() throws Exception {
        doThrow(new PermissionNotFoundException("GHOST"))
                .when(permissionUseCase).deletePermission("GHOST");

        mockMvc.perform(delete("/permissions/GHOST"))
                .andExpect(status().isNotFound());
    }
}
