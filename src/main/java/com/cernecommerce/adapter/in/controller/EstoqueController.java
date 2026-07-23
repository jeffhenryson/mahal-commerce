package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ProductRequest;
import com.cernecommerce.adapter.in.dtos.request.ReorderPointRequest;
import com.cernecommerce.adapter.in.dtos.request.StockMovementRequest;
import com.cernecommerce.adapter.in.dtos.request.WarehouseRequest;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WarehouseResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
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
 * Grade de produtos e controle de saldo multi-depósito do domínio <b>estoque</b>: cadastro de
 * SKU pai com variações (sabor/tamanho/cor), depósitos (loja física/e-commerce) e consulta de saldo.
 */
@RestController
@RequestMapping("/estoque")
@Tag(name = "Estoque", description = "Grade de produtos e inventário")
@SecurityRequirement(name = "bearerAuth")
public class EstoqueController {

    private final EstoqueUseCase estoqueUseCase;
    private final ProductDTOConverter converter;
    private final WarehouseDTOConverter warehouseConverter;
    private final StockMovementDTOConverter movementConverter;
    private final ApplicationEventPublisher publisher;

    public EstoqueController(EstoqueUseCase estoqueUseCase, ProductDTOConverter converter,
            WarehouseDTOConverter warehouseConverter, StockMovementDTOConverter movementConverter,
            ApplicationEventPublisher publisher) {
        this.estoqueUseCase = estoqueUseCase;
        this.converter = converter;
        this.warehouseConverter = warehouseConverter;
        this.movementConverter = movementConverter;
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

    @Operation(summary = "Cria um depósito (loja física ou e-commerce)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = WarehouseResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Código já cadastrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_MANAGE')")
    public ResponseEntity<WarehouseResponseDTO> createWarehouse(@Valid @RequestBody WarehouseRequest request,
            Authentication authentication) {
        Warehouse created = estoqueUseCase.createWarehouse(request.getCode(), request.getName(),
                warehouseConverter.toType(request.getType()));
        publisher.publishEvent(AuditEvent.of(EventType.WAREHOUSE_CREATED,
                authentication.getName(), Map.of("code", created.code())));
        return ResponseEntity.created(URI.create("/estoque/warehouses/" + created.code()))
                .body(warehouseConverter.toResponse(created));
    }

    @Operation(summary = "Lista todos os depósitos cadastrados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<List<WarehouseResponseDTO>> listWarehouses() {
        List<WarehouseResponseDTO> response = estoqueUseCase.listWarehouses().stream()
                .map(warehouseConverter::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consulta o saldo de um SKU em um depósito")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-balance")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<StockBalanceResponseDTO> getStockBalance(@RequestParam String sku,
            @RequestParam String warehouseCode) {
        StockBalance balance = estoqueUseCase.getStockBalance(sku, warehouseCode);
        return ResponseEntity.ok(warehouseConverter.toResponse(balance, warehouseCode));
    }

    @Operation(summary = "Registra uma movimentação manual de estoque (entrada, saída ou ajuste)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrada", content = @Content(schema = @Schema(implementation = StockBalanceResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente ou requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/movements")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockBalanceResponseDTO> registerMovement(@Valid @RequestBody StockMovementRequest request,
            Authentication authentication) {
        StockBalance updated = estoqueUseCase.adjustStock(request.getSku(), request.getWarehouseCode(),
                movementConverter.toType(request.getType()), request.getQuantity(), request.getReason(),
                authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("sku", request.getSku(), "warehouseCode", request.getWarehouseCode(),
                        "type", request.getType(), "quantity", request.getQuantity())));
        return ResponseEntity.created(URI.create("/estoque/stock-balance?sku=" + request.getSku()
                        + "&warehouseCode=" + request.getWarehouseCode()))
                .body(warehouseConverter.toResponse(updated, request.getWarehouseCode()));
    }

    @Operation(summary = "Define o ponto de reposição (quantidade mínima) de um SKU em um depósito")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Definido"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/products/{sku}/reorder-point")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<Void> setReorderPoint(@PathVariable String sku,
            @Valid @RequestBody ReorderPointRequest request, Authentication authentication) {
        estoqueUseCase.setReorderPoint(sku, request.getWarehouseCode(), request.getMinQuantity());
        publisher.publishEvent(AuditEvent.of(EventType.REORDER_POINT_SET, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", request.getWarehouseCode(),
                        "minQuantity", request.getMinQuantity())));
        return ResponseEntity.noContent().build();
    }
}
