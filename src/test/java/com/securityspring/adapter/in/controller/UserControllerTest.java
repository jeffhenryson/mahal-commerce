package com.security_spring.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security_spring.adapter.in.converter.UserDTOConverter;
import com.security_spring.adapter.in.dtos.request.UserRequestDTO;
import com.security_spring.core.domain.exception.RoleNotFoundException;
import com.security_spring.core.domain.exception.UserNotFoundException;
import com.security_spring.core.domain.exception.UsernameAlreadyExistsException;
import com.security_spring.core.domain.model.PageResult;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.in.UserUseCase;
import com.security_spring.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.List;

public class UserControllerTest {

    private MockMvc mockMvc;
    private UserUseCase useCase;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        useCase = mock(UserUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(useCase, new UserDTOConverter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private User user(Long id, String username) {
        User u = User.of(username, "hashed", new HashSet<>());
        u.setId(id);
        return u;
    }

    @Test
    void get_user_by_id_returns_200_with_user_data() throws Exception {
        when(useCase.getUserById(1L)).thenReturn(user(1L, "alice"));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void get_user_by_id_not_found_returns_404() throws Exception {
        when(useCase.getUserById(999L)).thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void delete_user_returns_204_and_calls_use_case() throws Exception {
        mockMvc.perform(delete("/users/42"))
                .andExpect(status().isNoContent());

        verify(useCase).deleteUser(42L);
    }

    @Test
    void delete_user_not_found_returns_404() throws Exception {
        doThrow(new UserNotFoundException(999L)).when(useCase).deleteUser(999L);

        mockMvc.perform(delete("/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_user_duplicate_username_returns_409() throws Exception {
        when(useCase.createUser(eq("admin"), any(), any()))
                .thenThrow(new UsernameAlreadyExistsException("admin"));

        UserRequestDTO dto = new UserRequestDTO();
        dto.setUsername("admin");
        dto.setPassword("Secret@123");

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_user_blank_username_returns_400() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_user_missing_body_returns_400() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assign_role_unknown_role_returns_404() throws Exception {
        doThrow(new RoleNotFoundException("ROLE_GHOST"))
                .when(useCase).assignRole("alice", "ROLE_GHOST");

        mockMvc.perform(post("/users/alice/roles/ROLE_GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void assign_role_unknown_user_returns_404() throws Exception {
        doThrow(new UserNotFoundException("ghost"))
                .when(useCase).assignRole("ghost", "ROLE_USER");

        mockMvc.perform(post("/users/ghost/roles/ROLE_USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void list_users_returns_paged_result() throws Exception {
        when(useCase.listAll(0, 20)).thenReturn(
                new PageResult<>(List.of(user(1L, "alice"), user(2L, "bob")), 0, 20, 2L, 1));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
}
