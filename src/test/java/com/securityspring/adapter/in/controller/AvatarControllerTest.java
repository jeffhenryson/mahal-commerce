package com.securityspring.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.securityspring.core.domain.exception.avatar.AvatarTooLargeException;
import com.securityspring.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.securityspring.core.ports.in.AvatarUseCase;
import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import com.securityspring.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

class AvatarControllerTest {

    private MockMvc mockMvc;
    private AvatarUseCase avatarUseCase;
    private AvatarStoragePort storagePort;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    private static final byte[] JPEG_BYTES = {(byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00};

    @BeforeEach
    void setup() {
        avatarUseCase = mock(AvatarUseCase.class);
        storagePort   = mock(AvatarStoragePort.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AvatarController(avatarUseCase, storagePort))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upload_jpeg_returns_200_with_avatar_url() throws Exception {
        when(avatarUseCase.upload(eq("alice"), any(), any()))
                .thenReturn("http://localhost:8080/avatars/uuid.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_BYTES);

        mockMvc.perform(multipart("/users/me/avatar").file(file).principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("http://localhost:8080/avatars/uuid.jpg"));
    }

    @Test
    void upload_too_large_returns_400_AVATAR_TOO_LARGE() throws Exception {
        when(avatarUseCase.upload(any(), any(), any()))
                .thenThrow(new AvatarTooLargeException(2 * 1024 * 1024));

        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_BYTES);

        mockMvc.perform(multipart("/users/me/avatar").file(file).principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AVATAR_TOO_LARGE"));
    }

    @Test
    void upload_invalid_format_returns_400_INVALID_AVATAR_FORMAT() throws Exception {
        when(avatarUseCase.upload(any(), any(), any()))
                .thenThrow(new InvalidAvatarFormatException());

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        mockMvc.perform(multipart("/users/me/avatar").file(file).principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AVATAR_FORMAT"));
    }

    @Test
    void delete_returns_204() throws Exception {
        mockMvc.perform(delete("/users/me/avatar").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(avatarUseCase).delete("alice");
    }

    @Test
    void serve_existing_file_returns_200_with_cache_headers() throws Exception {
        when(storagePort.load("uuid.jpg"))
                .thenReturn(Optional.of(new ByteArrayInputStream(JPEG_BYTES)));

        mockMvc.perform(get("/avatars/uuid.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=")));
    }

    @Test
    void serve_missing_file_returns_404() throws Exception {
        when(storagePort.load("missing.jpg")).thenReturn(Optional.empty());

        mockMvc.perform(get("/avatars/missing.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serve_filename_with_dotdot_returns_404() throws Exception {
        // "..secret.jpg" contém ".." — deve ser rejeitado pela guarda no controller
        mockMvc.perform(get("/avatars/..secret.jpg"))
                .andExpect(status().isNotFound());
    }

    // ── upload validation ────────────────────────────────────────────────────

    @Test
    void upload_empty_file_returns_400_INVALID_AVATAR_FORMAT() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/users/me/avatar").file(empty).principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AVATAR_FORMAT"));
    }
}
