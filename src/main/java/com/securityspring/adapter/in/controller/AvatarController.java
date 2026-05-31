package com.securityspring.adapter.in.controller;

import com.securityspring.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.securityspring.core.ports.in.AvatarUseCase;
import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
public class AvatarController {

    private final AvatarUseCase avatarUseCase;
    private final AvatarStoragePort storagePort;

    public AvatarController(AvatarUseCase avatarUseCase, AvatarStoragePort storagePort) {
        this.avatarUseCase = avatarUseCase;
        this.storagePort = storagePort;
    }

    @Operation(summary = "Faz upload do avatar do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/users/me/avatar")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file == null || file.isEmpty()) throw new InvalidAvatarFormatException();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidAvatarFormatException();
        }
        String avatarUrl = avatarUseCase.upload(authentication.getName(), bytes, file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    @Operation(summary = "Remove o avatar do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/users/me/avatar")
    public ResponseEntity<Void> delete(Authentication authentication) {
        avatarUseCase.delete(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Serve o arquivo de avatar (público, cache longo). " +
               "Com S3/CDN retorna 308 redirect para a URL pública; com armazenamento local serve os bytes.")
    @GetMapping("/avatars/{filename}")
    public ResponseEntity<?> serve(@PathVariable String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        // S3 / CDN: redireciona para URL pública com cache imutável no cliente
        Optional<String> publicUrl = storagePort.getPublicUrl(filename);
        if (publicUrl.isPresent()) {
            return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                    .header(HttpHeaders.LOCATION, publicUrl.get())
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                    .build();
        }
        // Armazenamento local: serve os bytes diretamente
        Optional<InputStream> stream = storagePort.load(filename);
        if (stream.isEmpty()) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = stream.get().readAllBytes();
            return ResponseEntity.ok()
                    .contentType(resolveMediaType(filename))
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
