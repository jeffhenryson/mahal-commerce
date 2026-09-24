package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.AddRoshExtraRequest;
import com.cernecommerce.adapter.in.dtos.request.AddSessionRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionAssetTypeRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionSettingsRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionTierRequest;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionAssetTypeResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionMenuResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionSettingsResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionTierResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cardápio de sessão da mesa (PDV-F021): o cadastro (faixas, utensílios, configuração — admin) e o
 * lançamento na comanda (sessão e 2º rosh — atendente). Controller próprio, como o do kit montável,
 * porque metade dele é cadastro e não mesa.
 */
@RestController
@Tag(name = "PDV (Sessão)", description = "Cardápio de sessão de narguilé: faixas, utensílios e duplo rosh")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvSessaoController {

    private final SessionMenuUseCase sessionMenuUseCase;
    private final ComandaUseCase comandaUseCase;
    private final CrmUseCase crmUseCase;
    private final ComandaDTOConverter comandaConverter;
    private final ApplicationEventPublisher publisher;

    public PdvSessaoController(SessionMenuUseCase sessionMenuUseCase, ComandaUseCase comandaUseCase,
            CrmUseCase crmUseCase, ComandaDTOConverter comandaConverter, ApplicationEventPublisher publisher) {
        this.sessionMenuUseCase = sessionMenuUseCase;
        this.comandaUseCase = comandaUseCase;
        this.crmUseCase = crmUseCase;
        this.comandaConverter = comandaConverter;
        this.publisher = publisher;
    }

    // ── Mesa (atendente) ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "Cardápio de sessão para a mesa",
            description = "Faixas ativas, utensílios com quantos estão livres agora, configuração "
                    + "(upgrade de vaso, dias de duplo rosh) e se hoje é dia de duplo rosh.")
    @GetMapping("/pdv/sessao/cardapio")
    @PreAuthorize("hasAnyAuthority('PDV_COMANDA_MANAGE', 'PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionMenuResponseDTO> menu() {
        return ResponseEntity.ok(SessionMenuResponseDTO.of(sessionMenuUseCase.getMenu()));
    }

    @Operation(summary = "Lança uma sessão do cardápio na mesa",
            description = "Preço = faixa (+ upgrade se vasoGrande), resolvido pelo servidor. A essência "
                    + "vai na nota da linha. Aloca vaso e utensílios inclusos; não move estoque.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançada, com a comanda atualizada"),
            @ApiResponse(responseCode = "404", description = "Comanda ou faixa não encontrada (SESSION_TIER_NOT_FOUND)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não aberta, sem utensílio livre (SESSION_ASSET_UNAVAILABLE) ou vaso não configurado (SESSION_MENU_CONFLICT)", content = @Content)
    })
    @PostMapping("/pdv/comandas/{id}/sessoes")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addSession(@PathVariable("id") Long comandaId,
            @Valid @RequestBody AddSessionRequest request, Authentication authentication) {
        Comanda comanda = comandaUseCase.addSession(comandaId, request.getTierId(), request.getEssencia(),
                request.isVasoGrande(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_SESSION_ADDED, authentication.getName(),
                Map.of("comandaId", comandaId, "tierId", request.getTierId(), "vasoGrande", request.isVasoGrande())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda));
    }

    @Operation(summary = "Lança o 2º rosh de uma sessão (duplo rosh)",
            description = "Nova essência, mesmos utensílios, ligado à sessão (fecha junto). De graça no "
                    + "primeiro rosh extra da sessão quando a mesa foi aberta em dia de duplo rosh; "
                    + "pelo preço da faixa nos demais casos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado, com a comanda atualizada"),
            @ApiResponse(responseCode = "404", description = "Comanda ou faixa não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "A linha não é uma sessão aberta desta comanda (NOT_A_SESSION_LINE)", content = @Content)
    })
    @PostMapping("/pdv/comandas/{id}/sessoes/{itemId}/rosh")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addRoshExtra(@PathVariable("id") Long comandaId,
            @PathVariable("itemId") Long sessionItemId, @Valid @RequestBody AddRoshExtraRequest request,
            Authentication authentication) {
        Comanda comanda = comandaUseCase.addRoshExtra(comandaId, sessionItemId, request.getTierId(),
                request.getEssencia(), authentication.getName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comandaId", comandaId);
        payload.put("sessionItemId", sessionItemId);
        if (request.getTierId() != null) {
            payload.put("tierId", request.getTierId());
        }
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_ROSH_EXTRA_ADDED, authentication.getName(), payload));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda));
    }

    // ── Cadastro (admin) ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "Lista as faixas de sessão (ativas e inativas)")
    @GetMapping("/pdv/sessao/faixas")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<List<SessionTierResponseDTO>> listTiers() {
        return ResponseEntity.ok(sessionMenuUseCase.listTiers().stream().map(SessionTierResponseDTO::of).toList());
    }

    @Operation(summary = "Cria uma faixa de sessão")
    @PostMapping("/pdv/sessao/faixas")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionTierResponseDTO> createTier(@Valid @RequestBody SessionTierRequest request,
            Authentication authentication) {
        var tier = sessionMenuUseCase.createTier(request.getNome(), request.getPreco(), request.getMarcas(),
                request.getOrdem());
        audit(authentication, "faixa", tier.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionTierResponseDTO.of(tier));
    }

    @Operation(summary = "Atualiza uma faixa de sessão",
            description = "Mudar o preço vale para lançamentos novos; linha já lançada mantém o preço congelado.")
    @PutMapping("/pdv/sessao/faixas/{id}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionTierResponseDTO> updateTier(@PathVariable Long id,
            @Valid @RequestBody SessionTierRequest request, Authentication authentication) {
        var tier = sessionMenuUseCase.updateTier(id, request.getNome(), request.getPreco(), request.getMarcas(),
                request.getOrdem(), request.getAtivo() == null || request.getAtivo());
        audit(authentication, "faixa", id);
        return ResponseEntity.ok(SessionTierResponseDTO.of(tier));
    }

    @Operation(summary = "Lista os utensílios da sessão")
    @GetMapping("/pdv/sessao/utensilios")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<List<SessionAssetTypeResponseDTO>> listAssetTypes() {
        return ResponseEntity.ok(sessionMenuUseCase.listAssetTypes().stream()
                .map(SessionAssetTypeResponseDTO::of).toList());
    }

    @Operation(summary = "Cria um tipo de utensílio")
    @PostMapping("/pdv/sessao/utensilios")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionAssetTypeResponseDTO> createAssetType(
            @Valid @RequestBody SessionAssetTypeRequest request, Authentication authentication) {
        if (request.getCodigo() == null || request.getCodigo().isBlank()) {
            throw new IllegalArgumentException("código do utensílio é obrigatório");
        }
        var type = sessionMenuUseCase.createAssetType(request.getCodigo(), request.getNome(),
                request.getQuantidadeTotal(), request.isIncluso());
        audit(authentication, "utensilio", type.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionAssetTypeResponseDTO.of(type));
    }

    @Operation(summary = "Atualiza um tipo de utensílio (nome, quantidade, incluso, ativo)")
    @PutMapping("/pdv/sessao/utensilios/{id}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionAssetTypeResponseDTO> updateAssetType(@PathVariable Long id,
            @Valid @RequestBody SessionAssetTypeRequest request, Authentication authentication) {
        var type = sessionMenuUseCase.updateAssetType(id, request.getNome(), request.getQuantidadeTotal(),
                request.isIncluso(), request.getAtivo() == null || request.getAtivo());
        audit(authentication, "utensilio", id);
        return ResponseEntity.ok(SessionAssetTypeResponseDTO.of(type));
    }

    @Operation(summary = "Configuração do cardápio de sessão")
    @GetMapping("/pdv/sessao/config")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionSettingsResponseDTO> getSettings() {
        return ResponseEntity.ok(SessionSettingsResponseDTO.of(sessionMenuUseCase.getSettings()));
    }

    @Operation(summary = "Atualiza a configuração: vasos, preço do upgrade e dias de duplo rosh")
    @PutMapping("/pdv/sessao/config")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionSettingsResponseDTO> updateSettings(@Valid @RequestBody SessionSettingsRequest request,
            Authentication authentication) {
        SessionSettings saved = sessionMenuUseCase.updateSettings(new SessionSettings(request.getVasoPadraoCodigo(),
                request.getVasoGrandeCodigo(), request.getUpgradeVasoGrandePreco(), request.getDiasDuploRosh()));
        audit(authentication, "config", 1L);
        return ResponseEntity.ok(SessionSettingsResponseDTO.of(saved));
    }

    private void audit(Authentication authentication, String target, Long id) {
        publisher.publishEvent(AuditEvent.of(EventType.SESSION_MENU_CHANGED, authentication.getName(),
                Map.of("target", target, "id", String.valueOf(id))));
    }

    /** Mesmo enriquecimento de nome de cliente de {@code PdvComandaController}. */
    private ComandaResponseDTO toResponse(Comanda comanda) {
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        if (dto.getCustomerId() != null) {
            dto.setCustomerName(crmUseCase.findCustomerNames(List.of(dto.getCustomerId())).get(dto.getCustomerId()));
        }
        return dto;
    }
}
