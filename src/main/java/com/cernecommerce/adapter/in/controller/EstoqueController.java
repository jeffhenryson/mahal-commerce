package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ProductRequest;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Grade de produtos do domínio <b>estoque</b>: cadastro de SKU pai com variações
 * (sabor/tamanho/cor) e listagem paginada.
 */
@RestController
@RequestMapping("/estoque")
@Tag(name = "Estoque", description = "Grade de produtos e inventário")
@SecurityRequirement(name = "bearerAuth")
public class EstoqueController {

    private final EstoqueUseCase estoqueUseCase;
    private final ProductDTOConverter converter;
    private final ApplicationEventPublisher publisher;

    public EstoqueController(EstoqueUseCase estoqueUseCase, ProductDTOConverter converter,
            ApplicationEventPublisher publisher) {
        this.estoqueUseCase = estoqueUseCase;
        this.converter = converter;
        this.publisher = publisher;
    }

    @Operation(summary = "Lista produtos paginados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<ProductResponseDTO>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<Product> result = estoqueUseCase.listProducts(page, Math.min(size, 100));
        PageResult<ProductResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cria um produto (SKU pai) com suas variações")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "SKU já cadastrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/products")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequest request,
            Authentication authentication) {
        List<ProductVariant> variants = converter.toVariants(request.getVariants());
        Product created = estoqueUseCase.createProduct(request.getSku(), request.getName(), request.getCategory(), variants);
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_CREATED,
                authentication.getName(), Map.of("sku", created.sku())));
        return ResponseEntity.created(URI.create("/estoque/products/" + created.sku()))
                .body(converter.toResponse(created));
    }
}
