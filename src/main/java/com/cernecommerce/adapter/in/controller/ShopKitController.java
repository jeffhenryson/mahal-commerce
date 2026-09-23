package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.KitBuilderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.KitSelectionRequest;
import com.cernecommerce.adapter.in.dtos.response.KitQuoteResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitStepOptionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitTemplateResponseDTO;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Montador de kit na vitrine (ECM-F008) — público pela mesma razão do catálogo: é navegação da
 * loja, lida sem login. Pôr no carrinho é autenticado e mora em {@link ShopKitCartController}.
 */
@RestController
@RequestMapping("/shop/kits")
@Tag(name = "Marketplace (Kit montável)", description = "Montador do Kit Mahal na vitrine")
@Validated
public class ShopKitController {

    private final KitBuilderUseCase kitBuilderUseCase;
    private final EstoqueUseCase estoqueUseCase;
    private final KitBuilderDTOConverter converter;

    public ShopKitController(KitBuilderUseCase kitBuilderUseCase, EstoqueUseCase estoqueUseCase,
            KitBuilderDTOConverter converter) {
        this.kitBuilderUseCase = kitBuilderUseCase;
        this.estoqueUseCase = estoqueUseCase;
        this.converter = converter;
    }

    @Operation(summary = "Kits montáveis à venda na vitrine")
    @GetMapping
    public ResponseEntity<List<KitTemplateResponseDTO>> list() {
        return ResponseEntity.ok(kitBuilderUseCase.listSellableTemplates(KitChannel.MARKETPLACE).stream()
                .map(converter::toResponse).toList());
    }

    @Operation(summary = "Detalhe de um kit montável, com os passos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Kit não encontrado ou fora de venda", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<KitTemplateResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(kitBuilderUseCase.getSellableTemplate(id, KitChannel.MARKETPLACE)));
    }

    @Operation(summary = "Opções de um passo do kit, com preço e disponibilidade no depósito do marketplace")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — lista vazia se a categoria não tem nada à venda"),
            @ApiResponse(responseCode = "404", description = "Kit não encontrado ou fora de venda", content = @Content),
            @ApiResponse(responseCode = "422", description = "Passo não pertence ao kit (KIT_STEP_NOT_FOUND)", content = @Content),
            @ApiResponse(responseCode = "503", description = "Depósito padrão do marketplace não configurado", content = @Content)
    })
    @GetMapping("/{id}/steps/{stepId}/options")
    public ResponseEntity<List<KitStepOptionResponseDTO>> options(@PathVariable Long id, @PathVariable Long stepId) {
        String warehouseCode = estoqueUseCase.getDefaultWarehouse().code();
        return ResponseEntity.ok(kitBuilderUseCase.listStepOptions(id, stepId, KitChannel.MARKETPLACE, warehouseCode)
                .stream().map(converter::toResponse).toList());
    }

    @Operation(summary = "Cota um kit montado sem pôr no carrinho",
            description = "Valida a escolha (passos obrigatórios, categoria, máximo por passo) e devolve "
                    + "o preço cheio, o desconto do kit e o total. Nada é gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "422", description = "Escolha não fecha um kit válido — o `code` diz qual regra (KIT_REQUIRED_STEP_MISSING, KIT_ITEM_NOT_IN_STEP_CATEGORY, ...)", content = @Content)
    })
    @PostMapping("/quote")
    public ResponseEntity<KitQuoteResponseDTO> quote(@Valid @RequestBody KitSelectionRequest request) {
        return ResponseEntity.ok(converter.toResponse(
                kitBuilderUseCase.quote(converter.toDomain(request), KitChannel.MARKETPLACE)));
    }
}
