package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.GoodsReceiptDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ActiveRequest;
import com.cernecommerce.adapter.in.dtos.request.GoodsReceiptRequest;
import com.cernecommerce.adapter.in.dtos.request.SupplierPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.SupplierRequest;
import com.cernecommerce.adapter.in.dtos.response.GoodsReceiptResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SupplierResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundException;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<PageResult<SupplierResponseDTO>> listSuppliers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // COM-C002 — o record de domínio não sai mais direto no contrato.
        PageResult<Supplier> result = comprasUseCase.listSuppliers(page, size);
        return ResponseEntity.ok(new PageResult<>(
                result.content().stream().map(SupplierResponseDTO::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Consulta um fornecedor por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SupplierResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Fornecedor não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_READ')")
    public ResponseEntity<SupplierResponseDTO> findSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(SupplierResponseDTO.from(comprasUseCase.findSupplierById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id))));
    }

    @Operation(summary = "Cadastra um fornecedor (COM-F001)",
            description = "Era o pedido nº 1 deste domínio, e destrava uma feature **já "
                    + "entregue**: a importação de NF-e por XML responde `404 "
                    + "SUPPLIER_NOT_FOUND_BY_TAX_ID` quando o CNPJ do emitente não está "
                    + "cadastrado, e até aqui não havia caminho pela UI para cadastrá-lo — só "
                    + "`INSERT` direto no banco.\n\n"
                    + "`taxId` aceita com ou sem máscara e é gravado **só com dígitos**, que é "
                    + "como o XML da nota traz o emitente. A duplicidade é conferida sobre o valor "
                    + "normalizado: `12.345.678/0001-99` e `12345678000199` são o mesmo "
                    + "fornecedor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cadastrado", content = @Content(schema = @Schema(implementation = SupplierResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Razão social ausente, ou CNPJ/CPF com número de dígitos inválido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe fornecedor com este CNPJ/CPF", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('COMPRAS_SUPPLIER_MANAGE')")
    public ResponseEntity<SupplierResponseDTO> registerSupplier(@Valid @RequestBody SupplierRequest request,
            Authentication authentication) {
        Supplier supplier = comprasUseCase.registerSupplier(request.getLegalName(), request.getTaxId(),
                request.getEmail());
        publisher.publishEvent(AuditEvent.of(EventType.SUPPLIER_CREATED, authentication.getName(),
                Map.of("supplierId", supplier.id(), "taxId", supplier.taxId())));
        return ResponseEntity.status(201).body(SupplierResponseDTO.from(supplier));
    }

    @Operation(summary = "Edita o cadastro de um fornecedor (COM-F001)",
            description = "PATCH parcial: campo ausente mantém o valor atual.\n\n"
                    + "`taxId` **não é editável** — é a chave pela qual a importação de NF-e "
                    + "encontra o fornecedor, e trocá-lo faria os recebimentos já registrados "
                    + "apontarem para um CNPJ que nunca os emitiu. Fornecedor com CNPJ errado se "
                    + "resolve criando o certo e desativando o outro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = SupplierResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Fornecedor não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('COMPRAS_SUPPLIER_MANAGE')")
    public ResponseEntity<SupplierResponseDTO> updateSupplier(@PathVariable Long id,
            @Valid @RequestBody SupplierPatchRequest request, Authentication authentication) {
        Supplier supplier = comprasUseCase.updateSupplier(id, request.getLegalName(), request.getEmail());
        publisher.publishEvent(AuditEvent.of(EventType.SUPPLIER_UPDATED, authentication.getName(),
                Map.of("supplierId", id)));
        return ResponseEntity.ok(SupplierResponseDTO.from(supplier));
    }

    @Operation(summary = "Ativa ou desativa um fornecedor",
            description = "Fornecedor inativo sai da lista de escolha de um recebimento novo, mas "
                    + "continua resolvendo os recebimentos já registrados — desativar não apaga "
                    + "histórico. Mesma semântica de `PATCH /estoque/products/{sku}/active`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = SupplierResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'active' ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Fornecedor não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/suppliers/{id}/active")
    @PreAuthorize("hasAuthority('COMPRAS_SUPPLIER_MANAGE')")
    public ResponseEntity<SupplierResponseDTO> setSupplierActive(@PathVariable Long id,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Supplier supplier = comprasUseCase.setSupplierActive(id, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                request.getActive() ? EventType.SUPPLIER_ACTIVATED : EventType.SUPPLIER_DEACTIVATED,
                authentication.getName(), Map.of("supplierId", id)));
        return ResponseEntity.ok(SupplierResponseDTO.from(supplier));
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
