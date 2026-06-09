package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.response.NotificationResponseDTO;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.notification.Notification;
import com.securityspring.core.ports.in.NotificationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class NotificationController {

    private final NotificationUseCase useCase;

    public NotificationController(NotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Lista notificações do usuário autenticado")
    @GetMapping
    public ResponseEntity<PageResult<NotificationResponseDTO>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        PageResult<Notification> result = useCase.getNotifications(auth.getName(), unreadOnly, page, size);
        PageResult<NotificationResponseDTO> response = new PageResult<>(
                result.content().stream().map(NotificationResponseDTO::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Retorna quantidade de notificações não lidas")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        long count = useCase.countUnread(auth.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Marca notificação específica como lida")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication auth) {
        useCase.markAsRead(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marca todas as notificações como lidas")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication auth) {
        useCase.markAllAsRead(auth.getName());
        return ResponseEntity.noContent().build();
    }
}
