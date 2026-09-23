package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.KitBuilderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.KitTemplateRequest;
import com.cernecommerce.adapter.in.dtos.response.KitTemplateResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Cadastro dos modelos de kit montável (EST-F031) — o "Kit Mahal" e afins. */
@RestController
@RequestMapping("/estoque/kit-templates")
@Tag(name = "Estoque (Kit montável)", description = "Modelos de kit que o cliente monta escolhendo um item por passo")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class KitTemplateController {

    private final KitBuilderUseCase kitBuilderUseCase;
    private final KitBuilderDTOConverter converter;
    private final ApplicationEventPublisher publisher;

    public KitTemplateController(KitBuilderUseCase kitBuilderUseCase, KitBuilderDTOConverter converter,
            ApplicationEventPublisher publisher) {
        this.kitBuilderUseCase = kitBuilderUseCase;
        this.converter = converter;
        this.publisher = publisher;
    }

    @Operation(summary = "Lista todos os modelos de kit montável, inclusive inativos")
    @GetMapping
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<KitTemplateResponseDTO>> list() {
        return ResponseEntity.ok(kitBuilderUseCase.listTemplates().stream().map(converter::toResponse).toList());
    }

    @Operation(summary = "Detalhe de um modelo de kit montável")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Modelo não encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<KitTemplateResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(kitBuilderUseCase.getTemplate(id)));
    }

    @Operation(summary = "Cria um modelo de kit montável",
            description = "Cada passo aponta para uma categoria do catálogo: as opções do passo são os "
                    + "produtos ativos e precificados daquela categoria. O preço do kit é a soma dos "
                    + "itens escolhidos menos `discountPercent`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoria de algum passo não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Nome já usado por outro kit (DUPLICATE_KIT_TEMPLATE_NAME)", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('ESTOQUE_KIT_TEMPLATE_MANAGE')")
    public ResponseEntity<KitTemplateResponseDTO> create(@Valid @RequestBody KitTemplateRequest request,
            Authentication authentication) {
        KitTemplate created = kitBuilderUseCase.createTemplate(converter.toDomain(request));
        publisher.publishEvent(AuditEvent.of(EventType.KIT_TEMPLATE_CREATED, authentication.getName(),
                Map.of("kitTemplateId", created.id(), "name", created.name())));
        return ResponseEntity.status(HttpStatus.CREATED).body(converter.toResponse(created));
    }

    @Operation(summary = "Substitui um modelo de kit montável",
            description = "Passo com `id` é atualizado no lugar, passo sem `id` é criado, passo que não "
                    + "veio é removido. Manter o `id` dos passos preserva os kits que já estão em "
                    + "carrinhos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado"),
            @ApiResponse(responseCode = "404", description = "Modelo ou categoria não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Nome já usado por outro kit", content = @Content)
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_KIT_TEMPLATE_MANAGE')")
    public ResponseEntity<KitTemplateResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody KitTemplateRequest request, Authentication authentication) {
        KitTemplate updated = kitBuilderUseCase.updateTemplate(id, converter.toDomain(request));
        publisher.publishEvent(AuditEvent.of(EventType.KIT_TEMPLATE_UPDATED, authentication.getName(),
                Map.of("kitTemplateId", updated.id(), "name", updated.name())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Exclui um modelo de kit montável",
            description = "Não desfaz venda nenhuma: pedidos e comandas guardam os itens como linhas "
                    + "comuns. Kit que estava em carrinho passa a ser recusado no checkout.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Excluído"),
            @ApiResponse(responseCode = "404", description = "Modelo não encontrado", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_KIT_TEMPLATE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        KitTemplate template = kitBuilderUseCase.getTemplate(id);
        kitBuilderUseCase.deleteTemplate(id);
        publisher.publishEvent(AuditEvent.of(EventType.KIT_TEMPLATE_DELETED, authentication.getName(),
                Map.of("kitTemplateId", id, "name", template.name())));
        return ResponseEntity.noContent().build();
    }
}
