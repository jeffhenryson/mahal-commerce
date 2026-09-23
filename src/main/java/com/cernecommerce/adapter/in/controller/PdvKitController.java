package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.KitBuilderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.KitSelectionRequest;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitQuoteResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitStepOptionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitTemplateResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Kit montável no PDV (PDV-F019): o atendente monta o Kit Mahal com o cliente e lança na comanda.
 * Controller próprio, e não mais métodos em {@code PdvComandaController}, porque metade dele é
 * catálogo ({@code /pdv/kits}) e não mesa.
 */
@RestController
@Tag(name = "PDV (Kit montável)", description = "Montador do Kit Mahal no balcão e na mesa")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvKitController {

    private final KitBuilderUseCase kitBuilderUseCase;
    private final ComandaUseCase comandaUseCase;
    private final CrmUseCase crmUseCase;
    private final KitBuilderDTOConverter kitConverter;
    private final ComandaDTOConverter comandaConverter;
    private final ApplicationEventPublisher publisher;

    public PdvKitController(KitBuilderUseCase kitBuilderUseCase, ComandaUseCase comandaUseCase,
            CrmUseCase crmUseCase, KitBuilderDTOConverter kitConverter, ComandaDTOConverter comandaConverter,
            ApplicationEventPublisher publisher) {
        this.kitBuilderUseCase = kitBuilderUseCase;
        this.comandaUseCase = comandaUseCase;
        this.crmUseCase = crmUseCase;
        this.kitConverter = kitConverter;
        this.comandaConverter = comandaConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Kits montáveis à venda no PDV")
    @GetMapping("/pdv/kits")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<KitTemplateResponseDTO>> list() {
        return ResponseEntity.ok(kitBuilderUseCase.listSellableTemplates(KitChannel.PDV).stream()
                .map(kitConverter::toResponse).toList());
    }

    @Operation(summary = "Opções de um passo do kit no PDV",
            description = "`warehouseCode` opcional: com ele, cada opção traz `available`; sem ele, `available` vem nulo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Kit não encontrado ou fora de venda no PDV", content = @Content),
            @ApiResponse(responseCode = "422", description = "Passo não pertence ao kit (KIT_STEP_NOT_FOUND)", content = @Content)
    })
    @GetMapping("/pdv/kits/{id}/steps/{stepId}/options")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<KitStepOptionResponseDTO>> options(@PathVariable Long id, @PathVariable Long stepId,
            @RequestParam(required = false) String warehouseCode) {
        return ResponseEntity.ok(kitBuilderUseCase.listStepOptions(id, stepId, KitChannel.PDV, warehouseCode)
                .stream().map(kitConverter::toResponse).toList());
    }

    @Operation(summary = "Cota um kit montado no PDV, sem lançar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "422", description = "Escolha não fecha um kit válido — ver `code`", content = @Content)
    })
    @PostMapping("/pdv/kits/quote")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<KitQuoteResponseDTO> quote(@Valid @RequestBody KitSelectionRequest request) {
        return ResponseEntity.ok(kitConverter.toResponse(
                kitBuilderUseCase.quote(kitConverter.toDomain(request), KitChannel.PDV)));
    }

    @Operation(summary = "Lança um kit montado na comanda",
            description = "Uma linha por item escolhido, todas com o mesmo `kitBundleId` e cada uma com a sua "
                    + "parte do desconto do kit (`kitDiscountAmount`). O estoque de cada item sai agora, "
                    + "como num lançamento avulso. No fechamento, o desconto do kit soma ao desconto de "
                    + "conta mas não conta para o teto de desconto do atendente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado, com a comanda atualizada"),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente de algum item", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta ou sessão de caixa encerrada", content = @Content),
            @ApiResponse(responseCode = "422", description = "Escolha não fecha um kit válido — ver `code`", content = @Content)
    })
    @PostMapping("/pdv/comandas/{id}/kits")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addKit(@PathVariable("id") Long comandaId,
            @Valid @RequestBody KitSelectionRequest request, Authentication authentication) {
        Comanda comanda = comandaUseCase.addKit(comandaId, kitConverter.toDomain(request), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_KIT_ADDED, authentication.getName(),
                Map.of("comandaId", comandaId, "kitTemplateId", request.getTemplateId(),
                        "skus", request.getPicks().stream().map(KitSelectionRequest.Pick::getSku).toList())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda));
    }

    @Operation(summary = "Tira um kit inteiro da comanda, devolvendo o estoque de cada item",
            description = "Linha de kit não sai sozinha por `DELETE /pdv/comandas/{id}/items/{itemId}` "
                    + "(409 KIT_ITEM_REMOVAL_NOT_ALLOWED): o desconto foi rateado pelo pacote.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Removido, com a comanda atualizada"),
            @ApiResponse(responseCode = "404", description = "Comanda ou kit não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta ou sessão de caixa encerrada", content = @Content)
    })
    @DeleteMapping("/pdv/comandas/{id}/kits/{bundleId}")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> removeKit(@PathVariable("id") Long comandaId,
            @PathVariable String bundleId, Authentication authentication) {
        Comanda comanda = comandaUseCase.removeKit(comandaId, bundleId, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_KIT_REMOVED, authentication.getName(),
                Map.of("comandaId", comandaId, "kitBundleId", bundleId)));
        return ResponseEntity.ok(toResponse(comanda));
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
