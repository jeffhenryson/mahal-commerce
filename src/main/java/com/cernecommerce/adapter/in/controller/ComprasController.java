package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.GoodsReceiptDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.GoodsReceiptRequest;
import com.cernecommerce.adapter.in.dtos.response.GoodsReceiptResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Fornecedores e entradas de mercadoria do domínio <b>compras</b>: consulta de fornecedores
 * e recebimento de mercadoria com entrada automática no estoque.
 */
@RestController
@RequestMapping("/compras")
@Tag(name = "Compras", description = "Fornecedores e entradas de mercadorias")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ComprasController {

    private final ComprasUseCase comprasUseCase;
    private final GoodsReceiptDTOConverter goodsReceiptConverter;
    private final ApplicationEventPublisher publisher;

    public ComprasController(ComprasUseCase comprasUseCase, GoodsReceiptDTOConverter goodsReceiptConverter,
            ApplicationEventPublisher publisher) {
        this.comprasUseCase = comprasUseCase;
        this.goodsReceiptConverter = goodsReceiptConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Lista fornecedores paginados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('COMPRAS_READ')")
    public ResponseEntity<PageResult<Supplier>> listSuppliers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(comprasUseCase.listSuppliers(page, size));
    }

    @Operation(summary = "Registra o recebimento de mercadoria de um fornecedor e dá entrada automática no estoque")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrado", content = @Content(schema = @Schema(implementation = GoodsReceiptResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Fornecedor ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/goods-receipts")
    @PreAuthorize("hasAuthority('COMPRAS_RECEIPT_MANAGE')")
    public ResponseEntity<GoodsReceiptResponseDTO> receiveGoods(@Valid @RequestBody GoodsReceiptRequest request,
            Authentication authentication) {
        List<GoodsReceiptItem> items = goodsReceiptConverter.toItems(request.getItems());
        GoodsReceipt receipt = comprasUseCase.receiveGoods(request.getSupplierId(), request.getWarehouseCode(),
                items, authentication.getName());
        // EST-C004: mesma lacuna da venda — a entrada de mercadoria movimentava saldo sem deixar
        // trilha. Um evento por recebimento, com os SKUs afetados.
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "GOODS_RECEIPT",
                        "supplierId", request.getSupplierId(),
                        "warehouseCode", request.getWarehouseCode(),
                        "type", MovementType.ENTRADA.name(),
                        "skus", items.stream().map(GoodsReceiptItem::sku).toList(),
                        "itemCount", items.size())));
        return ResponseEntity.status(201).body(goodsReceiptConverter.toResponse(receipt));
    }
}
