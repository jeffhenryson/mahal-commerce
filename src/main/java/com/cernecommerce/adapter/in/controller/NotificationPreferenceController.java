package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.UpdateNotificationPreferenceRequest;
import com.cernecommerce.adapter.in.dtos.response.NotificationPreferenceResponseDTO;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationPreferenceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications/preferences")
@SecurityRequirement(name = "bearerAuth")
public class NotificationPreferenceController {

    private final NotificationPreferenceUseCase useCase;

    public NotificationPreferenceController(NotificationPreferenceUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Lista preferências de notificação do usuário autenticado")
    @GetMapping
    public ResponseEntity<List<NotificationPreferenceResponseDTO>> getPreferences(Authentication auth) {
        List<NotificationPreferenceResponseDTO> prefs = useCase.getPreferences(auth.getName()).stream()
                .map(NotificationPreferenceResponseDTO::from)
                .toList();
        return ResponseEntity.ok(prefs);
    }

    @Operation(summary = "Atualiza preferência de notificação para um tipo específico")
    @PutMapping("/{type}")
    public ResponseEntity<NotificationPreferenceResponseDTO> updatePreference(
            @PathVariable NotificationType type,
            @RequestBody UpdateNotificationPreferenceRequest request,
            Authentication auth) {
        useCase.updatePreference(auth.getName(), type, request.inAppEnabled(), request.emailEnabled());
        return ResponseEntity.ok(new NotificationPreferenceResponseDTO(type.name(), request.inAppEnabled(), request.emailEnabled()));
    }
}
