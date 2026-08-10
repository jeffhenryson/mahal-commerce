package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.CategoryDTOConverter;
import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.StockCountDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.StockReservationDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ActiveRequest;
import com.cernecommerce.adapter.in.dtos.request.CategoryPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.CategoryRequest;
import com.cernecommerce.adapter.in.dtos.request.LotTrackedRequest;
import com.cernecommerce.adapter.in.dtos.request.KitRecipeRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequest;
import com.cernecommerce.adapter.in.dtos.request.ReorderPointRequest;
import com.cernecommerce.adapter.in.dtos.request.StockCountItemRequest;
import com.cernecommerce.adapter.in.dtos.request.StockCountRequest;
import com.cernecommerce.adapter.in.dtos.request.StockMovementRequest;
import com.cernecommerce.adapter.in.dtos.request.WarehousePatchRequest;
import com.cernecommerce.adapter.in.dtos.request.WarehouseRequest;
import com.cernecommerce.adapter.in.dtos.response.CategoryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitComponentResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.LotIntegrityMismatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrphanSkuResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PricingResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ReorderPointResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ReservationIntegrityMismatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockCountResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockLotResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockMovementResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockReservationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WarehouseResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.exception.storage.InvalidImageFormatException;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.ProductImageUseCase;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grade de produtos e controle de saldo multi-depósito do domínio <b>estoque</b>: cadastro de
 * SKU pai com variações (sabor/tamanho/cor), depósitos (loja física/e-commerce) e consulta de saldo.
 */
@RestController
@RequestMapping("/estoque")
@Tag(name = "Estoque", description = "Grade de produtos e inventário")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class EstoqueController {

    private final EstoqueUseCase estoqueUseCase;
    private final ProductDTOConverter converter;
    private final WarehouseDTOConverter warehouseConverter;
    private final StockMovementDTOConverter movementConverter;
    private final StockCountDTOConverter stockCountConverter;
    private final StockReservationDTOConverter reservationConverter;
    private final CategoryDTOConverter categoryConverter;
    private final ProductImageUseCase productImageUseCase;
    private final ApplicationEventPublisher publisher;

    public EstoqueController(EstoqueUseCase estoqueUseCase, ProductDTOConverter converter,
            WarehouseDTOConverter warehouseConverter, StockMovementDTOConverter movementConverter,
            StockCountDTOConverter stockCountConverter, StockReservationDTOConverter reservationConverter,
            CategoryDTOConverter categoryConverter, ProductImageUseCase productImageUseCase,
            ApplicationEventPublisher publisher) {
        this.estoqueUseCase = estoqueUseCase;
        this.converter = converter;
        this.warehouseConverter = warehouseConverter;
        this.movementConverter = movementConverter;
        this.stockCountConverter = stockCountConverter;
        this.reservationConverter = reservationConverter;
        this.categoryConverter = categoryConverter;
        this.productImageUseCase = productImageUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Lista produtos paginados, com busca, filtros e ordenação",
            description = "Todos os filtros são opcionais e combináveis; omitir todos devolve o "
                    + "catálogo inteiro paginado, como antes. `search` casa por trecho em nome "
                    + "OU SKU, sem diferenciar maiúsculas. `category` e `brand` são igualdade "
                    + "exata (também sem diferenciar maiúsculas). A ordenação sempre desempata "
                    + "por id, para a paginação ser estável.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Parâmetro fora da faixa ou valor de enum inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<ProductResponseDTO>> listProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "ID") ProductSortField sort,
            @RequestParam(defaultValue = "ASC") SortDirection direction) {
        ProductFilter filter = new ProductFilter(search, category, brand, active);
        PageResult<Product> result = estoqueUseCase.listProducts(page, size, filter, sort, direction);
        PageResult<ProductResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Busca um produto por SKU",
            description = "Aceita o SKU do produto pai ou o de qualquer variação — nos dois casos "
                    + "devolve o produto pai, que é onde moram categoria e precificação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<ProductResponseDTO> getProduct(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(converter.toResponse(estoqueUseCase.findProductBySku(sku)));
    }

    // ── Categorias do catálogo ───────────────────────────────────────────────

    @Operation(summary = "Lista categorias do catálogo, incluindo as inativas",
            description = "Ordenadas por destaque, depois ordem de exibição, depois nome — a mesma "
                    + "ordem que a vitrine usa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PageResult<CategoryResponseDTO>> listCategories(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<Category> result = estoqueUseCase.listCategories(page, size);
        return ResponseEntity.ok(new PageResult<>(
                result.content().stream().map(categoryConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Cria uma categoria do catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada"),
            @ApiResponse(responseCode = "409", description = "Já existe categoria com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequest request,
            Authentication authentication) {
        Category created = estoqueUseCase.createCategory(request.getName(), request.isFeatured(),
                request.getDisplayOrder());
        publisher.publishEvent(AuditEvent.of(EventType.CATEGORY_CREATED,
                authentication.getName(), Map.of("categoryId", String.valueOf(created.id()),
                        "name", created.name())));
        return ResponseEntity.created(URI.create("/estoque/categories/" + created.id()))
                .body(categoryConverter.toResponse(created));
    }

    @Operation(summary = "Altera parcialmente uma categoria (nome, destaque e/ou ordem)",
            description = "Campo ausente ou nulo é mantido. Renomear propaga o novo nome para "
                    + "todos os produtos vinculados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe categoria com esse nome", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponseDTO> updateCategory(@PathVariable Long id,
            @Valid @RequestBody CategoryPatchRequest request, Authentication authentication) {
        Category updated = estoqueUseCase.updateCategory(id, request.getName(), request.getFeatured(),
                request.getDisplayOrder());
        publisher.publishEvent(AuditEvent.of(EventType.CATEGORY_UPDATED,
                authentication.getName(), Map.of("categoryId", String.valueOf(id))));
        return ResponseEntity.ok(categoryConverter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa uma categoria",
            description = "Categoria inativa some da vitrine, mas os produtos vinculados continuam "
                    + "à venda — categoria é organização de vitrine, não permissão de venda.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/categories/{id}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_CATEGORY_MANAGE')")
    public ResponseEntity<CategoryResponseDTO> setCategoryActive(@PathVariable Long id,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Category updated = estoqueUseCase.setCategoryActive(id, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                Boolean.TRUE.equals(request.getActive()) ? EventType.CATEGORY_ACTIVATED : EventType.CATEGORY_DEACTIVATED,
                authentication.getName(), Map.of("categoryId", String.valueOf(id))));
        return ResponseEntity.ok(categoryConverter.toResponse(updated));
    }

    @Operation(summary = "Faz upload de uma imagem de produto e devolve a URL pública",
            description = "Recebe `multipart/form-data` com o campo `file` (JPEG, PNG ou WebP). "
                    + "Não recebe SKU: a imagem é enviada enquanto o produto ainda está sendo "
                    + "preenchido, e a URL devolvida é usada depois em `imageUrl`/`images` do "
                    + "cadastro. A URL é pública e servida por `GET /product-images/{filename}`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Arquivo ausente, formato não aceito ou acima do limite", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping(value = "/products/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file == null || file.isEmpty()) throw new InvalidImageFormatException();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            // Falha ao ler o corpo do multipart é requisição malformada, não erro do servidor —
            // mesmo tratamento que AvatarController dá ao caso.
            throw new InvalidImageFormatException();
        }
        String imageUrl = productImageUseCase.upload(bytes);
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_IMAGE_UPLOADED,
                authentication.getName(), Map.of("imageUrl", imageUrl)));
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @Operation(summary = "Cria um produto (SKU pai) com suas variações e, opcionalmente, sua precificação",
            description = "O bloco `pricing` é opcional — omitido, o produto nasce sem preço. "
                    + "Enviá-lo exige `ESTOQUE_PRODUCT_PRICE_MANAGE` além de `ESTOQUE_PRODUCT_MANAGE`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "SKU já cadastrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/products")
    // touchesPricing() e não "#request.pricing == null": desde EST-F020 o preço também pode vir
    // dentro de variants[], e checar só a raiz deixaria quem tem apenas ESTOQUE_PRODUCT_MANAGE
    // precificar pela porta lateral.
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE') "
            + "and (!#request.touchesPricing() or hasAuthority('ESTOQUE_PRODUCT_PRICE_MANAGE'))")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequest request,
            Authentication authentication) {
        List<ProductVariant> variants = converter.toVariants(request.getVariants());
        Product created = estoqueUseCase.createProduct(request.getSku(), request.getName(), request.getCategory(),
                variants, converter.toPricing(request.getPricing()), request.getBrand(), request.getImageUrl(),
                request.isOnSale(), request.isSuperPromo(), request.getDescription(), request.getVideoUrl(),
                request.getImages(), converter.toAttributes(request.getAttributes()), request.getCategoryId());
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_CREATED,
                authentication.getName(), Map.of("sku", created.sku())));
        return ResponseEntity.created(URI.create("/estoque/products/" + created.sku()))
                .body(converter.toResponse(created));
    }

    @Operation(summary = "Altera parcialmente um produto (nome, categoria e/ou precificação)",
            description = "Campo ausente ou nulo é mantido, inclusive dentro de `pricing`. "
                    + "Não altera o SKU nem as variações. Mandar o bloco `pricing` exige "
                    + "`ESTOQUE_PRODUCT_PRICE_MANAGE` além de `ESTOQUE_PRODUCT_MANAGE`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}")
    // touchesPricing() e não "#request.pricing == null": desde EST-F020 o preço também pode vir
    // dentro de variants[], e checar só a raiz deixaria quem tem apenas ESTOQUE_PRODUCT_MANAGE
    // precificar pela porta lateral.
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE') "
            + "and (!#request.touchesPricing() or hasAuthority('ESTOQUE_PRODUCT_PRICE_MANAGE'))")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ProductPatchRequest request, Authentication authentication) {
        // Atributos ausentes precisam chegar como null ("não mexer"), não como lista vazia
        // ("apagar todos") — por isso a conversão só acontece quando o campo veio no corpo.
        Product updated = estoqueUseCase.updateProduct(sku, request.getName(), request.getCategory(),
                converter.toPricing(request.getPricing()), request.getBrand(), request.getImageUrl(),
                request.getOnSale(), request.getSuperPromo(), request.getDescription(), request.getVideoUrl(),
                request.getImages(),
                request.getAttributes() == null ? null : converter.toAttributes(request.getAttributes()),
                request.getCategoryId());
        publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_UPDATED,
                authentication.getName(), Map.of("sku", updated.sku())));
        // Evento próprio para mudança de preço: quem baixou o preço de quê e quando é a pergunta
        // que se faz depois de um fechamento de caixa estranho, e ela não pode se perder no meio
        // dos PRODUCT_UPDATED de renomeação de produto.
        if (request.getPricing() != null) {
            publisher.publishEvent(AuditEvent.of(EventType.PRODUCT_PRICE_CHANGED,
                    authentication.getName(), Map.of(
                            "sku", updated.sku(),
                            "effectivePrice", String.valueOf(updated.pricing().effectivePrice()))));
        }
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Consulta a precificação vigente de um SKU",
            description = "Aceita SKU pai ou de variação — a variação herda o preço do pai. "
                    + "Devolve os valores derivados (preço sugerido, preço efetivo, margem) já "
                    + "calculados, para o PDV e a vitrine não reimplementarem o arredondamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PricingResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado no catálogo", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/price")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<PricingResponseDTO> getProductPrice(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(converter.toResponse(estoqueUseCase.findPricingBySku(sku)));
    }

    @Operation(summary = "Ativa ou desativa um produto",
            description = "Produto desativado recusa entrada de estoque (manual ou por recebimento de Compras), "
                    + "mas continua aceitando saída e venda, para escoar o saldo remanescente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'active' ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> setProductActive(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.setProductActive(sku, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                request.getActive() ? EventType.PRODUCT_ACTIVATED : EventType.PRODUCT_DEACTIVATED,
                authentication.getName(), Map.of("sku", updated.sku())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa o rastreamento de lote e validade de um produto (EST-F008)",
            description = "Opt-in por SKU — só essência/carvão/perecível costuma precisar. A partir daqui, "
                    + "ENTRADA deste SKU em POST /estoque/movements passa a exigir lotCode e expiryDate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'lotTracked' ausente, ou SKU é KIT", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/products/{sku}/lot-tracked")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> setProductLotTracked(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody LotTrackedRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.setProductLotTracked(sku, request.getLotTracked());
        publisher.publishEvent(AuditEvent.of(
                request.getLotTracked() ? EventType.PRODUCT_LOT_TRACKED_ENABLED : EventType.PRODUCT_LOT_TRACKED_DISABLED,
                authentication.getName(), Map.of("sku", updated.sku())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Lista os lotes de um SKU em um depósito (EST-F008)",
            description = "Do que vence primeiro em diante. Lista vazia se o SKU não é lote-rastreado ou "
                    + "ainda não recebeu nenhum lote — não é erro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — lista vazia se não houver lote"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/lots")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<StockLotResponseDTO>> listStockLots(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        return ResponseEntity.ok(estoqueUseCase.listStockLots(sku, warehouseCode).stream()
                .map(lot -> converter.toResponse(lot, warehouseCode)).toList());
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

    @Operation(summary = "Altera parcialmente um depósito (nome e/ou tipo)",
            description = "Campo ausente ou nulo é mantido. Não altera o código.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = WarehouseResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/warehouses/{code}")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_MANAGE')")
    public ResponseEntity<WarehouseResponseDTO> updateWarehouse(
            @PathVariable @NotBlank @Size(min = 2, max = 50) String code,
            @Valid @RequestBody WarehousePatchRequest request, Authentication authentication) {
        Warehouse updated = estoqueUseCase.updateWarehouse(code, request.getName(),
                warehouseConverter.toTypeOrNull(request.getType()));
        publisher.publishEvent(AuditEvent.of(EventType.WAREHOUSE_UPDATED,
                authentication.getName(), Map.of("code", updated.code())));
        return ResponseEntity.ok(warehouseConverter.toResponse(updated));
    }

    @Operation(summary = "Ativa ou desativa um depósito",
            description = "Depósito desativado recusa entrada de estoque, mas continua despachando saída.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alterado", content = @Content(schema = @Schema(implementation = WarehouseResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Campo 'active' ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/warehouses/{code}/active")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_MANAGE')")
    public ResponseEntity<WarehouseResponseDTO> setWarehouseActive(
            @PathVariable @NotBlank @Size(min = 2, max = 50) String code,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        Warehouse updated = estoqueUseCase.setWarehouseActive(code, request.getActive());
        publisher.publishEvent(AuditEvent.of(
                request.getActive() ? EventType.WAREHOUSE_ACTIVATED : EventType.WAREHOUSE_DEACTIVATED,
                authentication.getName(), Map.of("code", updated.code())));
        return ResponseEntity.ok(warehouseConverter.toResponse(updated));
    }

    @Operation(summary = "Lista depósitos paginados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Parâmetro de paginação inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<PageResult<WarehouseResponseDTO>> listWarehouses(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<Warehouse> result = estoqueUseCase.listWarehouses(page, size);
        PageResult<WarehouseResponseDTO> response = new PageResult<>(
                result.content().stream().map(warehouseConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consulta o saldo de estoque em um depósito",
            description = "`sku` é opcional. Informado, devolve o saldo desse produto (objeto único). "
                    + "Omitido, devolve o saldo paginado de todos os produtos do depósito (`PageResult`), "
                    + "para telas de alertas de reposição.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-balance")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<?> getStockBalance(
            @RequestParam(required = false) @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (sku != null) {
            StockBalance balance = estoqueUseCase.getStockBalance(sku, warehouseCode);
            return ResponseEntity.ok(warehouseConverter.toResponse(balance, warehouseCode));
        }
        PageResult<StockBalance> result = estoqueUseCase.listStockBalances(warehouseCode, page, size);
        PageResult<StockBalanceResponseDTO> response = new PageResult<>(
                result.content().stream().map(b -> warehouseConverter.toResponse(b, warehouseCode)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
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
        // Só usa a sobrecarga com lote quando o request de fato traz lotCode/expiryDate — mantém
        // o caminho sem lote (a maioria dos SKUs) idêntico ao de antes do EST-F008.
        StockBalance updated = request.getLotCode() == null && request.getExpiryDate() == null
                ? estoqueUseCase.adjustStock(request.getSku(), request.getWarehouseCode(),
                        movementConverter.toType(request.getType()), request.getQuantity(), request.getReason(),
                        authentication.getName())
                : estoqueUseCase.adjustStock(request.getSku(), request.getWarehouseCode(),
                        movementConverter.toType(request.getType()), request.getQuantity(), request.getReason(),
                        authentication.getName(), request.getLotCode(), request.getExpiryDate());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("sku", request.getSku(), "warehouseCode", request.getWarehouseCode(),
                        "type", request.getType(), "quantity", request.getQuantity())));
        return ResponseEntity.created(URI.create("/estoque/stock-balance?sku=" + request.getSku()
                        + "&warehouseCode=" + request.getWarehouseCode()))
                .body(warehouseConverter.toResponse(updated, request.getWarehouseCode()));
    }

    @Operation(summary = "Lista o histórico paginado de movimentações de estoque",
            description = "`sku` e `warehouseCode` são opcionais. Omitidos, devolve o feed geral de "
                    + "movimentações (mais recentes primeiro); informados, filtram por esse SKU e/ou depósito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<StockMovementResponseDTO>> listMovements(
            @RequestParam(required = false) @Size(min = 3, max = 50) String sku,
            @RequestParam(required = false) @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockMovement> result = estoqueUseCase.listMovements(sku, warehouseCode, page, size);
        Map<Long, String> warehouseCodesById = warehouseCode != null ? Map.of() : resolveWarehouseCodes(result);
        PageResult<StockMovementResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(m -> movementConverter.toResponse(m,
                                warehouseCode != null ? warehouseCode : warehouseCodesById.get(m.warehouseId())))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve, num lote só, o {@code code} dos depósitos distintos que aparecem numa página de
     * movimentações sem {@code warehouseCode} informado — a entidade só guarda {@code warehouseId}.
     */
    private Map<Long, String> resolveWarehouseCodes(PageResult<StockMovement> result) {
        Map<Long, String> codesById = new HashMap<>();
        for (StockMovement movement : result.content()) {
            codesById.computeIfAbsent(movement.warehouseId(),
                    id -> estoqueUseCase.getWarehouse(id).code());
        }
        return codesById;
    }

    @Operation(summary = "Define o ponto de reposição (quantidade mínima) de um SKU em um depósito")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Definido"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/products/{sku}/reorder-point")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<Void> setReorderPoint(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody ReorderPointRequest request, Authentication authentication) {
        estoqueUseCase.setReorderPoint(sku, request.getWarehouseCode(), request.getMinQuantity());
        publisher.publishEvent(AuditEvent.of(EventType.REORDER_POINT_SET, authentication.getName(),
                Map.of("sku", sku, "warehouseCode", request.getWarehouseCode(),
                        "minQuantity", request.getMinQuantity())));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consulta o ponto de reposição de um SKU em um depósito",
            description = "`minQuantity` nulo significa que não há ponto de reposição configurado "
                    + "para este SKU/depósito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/reorder-point")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<ReorderPointResponseDTO> getReorderPoint(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        Optional<ReorderPoint> reorderPoint = estoqueUseCase.getReorderPoint(sku, warehouseCode);
        return ResponseEntity.ok(warehouseConverter.toResponse(reorderPoint.orElse(null), sku, warehouseCode));
    }

    @Operation(summary = "Lista paginada dos pontos de reposição configurados em um depósito",
            description = "Alimenta telas de alertas de reposição junto com GET /estoque/stock-balance "
                    + "(sem `sku`) — o cliente cruza os dois lotes por SKU.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/reorder-points")
    @PreAuthorize("hasAuthority('ESTOQUE_WAREHOUSE_READ')")
    public ResponseEntity<PageResult<ReorderPointResponseDTO>> listReorderPoints(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<ReorderPoint> result = estoqueUseCase.listReorderPoints(warehouseCode, page, size);
        PageResult<ReorderPointResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(rp -> warehouseConverter.toResponse(rp, rp.sku(), warehouseCode))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------------------------
    // Kits (EST-F015) — virtuais, de um nível só (§2.10 do plano)
    // ------------------------------------------------------------------------------------

    @Operation(summary = "Define (substitui integralmente) a receita de um kit",
            description = "PUT idempotente: promove o produto a KIT como efeito colateral. Componente "
                    + "precisa ser SIMPLES — kit dentro de kit é proibido por construção.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Definida", content = @Content(schema = @Schema(implementation = ProductResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "SKU do kit ou de algum componente não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Receita vazia, autorreferência, componente duplicado, componente não SIMPLES, kit com variações, ou SKU já é componente de outro kit", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/products/{sku}/kit")
    @PreAuthorize("hasAuthority('ESTOQUE_KIT_MANAGE')")
    public ResponseEntity<ProductResponseDTO> defineKitRecipe(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku,
            @Valid @RequestBody KitRecipeRequest request, Authentication authentication) {
        Product updated = estoqueUseCase.defineKitRecipe(sku, converter.toKitComponentCommands(request));
        publisher.publishEvent(AuditEvent.of(EventType.KIT_RECIPE_CHANGED, authentication.getName(),
                Map.of("sku", sku, "componentCount", request.getComponents().size())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Consulta a receita vigente de um kit",
            description = "Lista vazia se o SKU existe mas nunca foi promovido a kit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/products/{sku}/kit")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<KitComponentResponseDTO>> getKitRecipe(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(estoqueUseCase.getKitRecipe(sku).stream()
                .map(converter::toKitComponentResponse).toList());
    }

    // ------------------------------------------------------------------------------------
    // Reserva de estoque (EST-F013/EST-F021). Só leitura aqui: quem cria, consome e libera
    // reserva hoje é o próprio módulo (checkout futuro e liquidação de pedido online no PDV),
    // não um operador via HTTP — daí não existirem POST/{id}/release neste controller.
    // ------------------------------------------------------------------------------------

    @Operation(summary = "Lista reservas de estoque paginadas, mais recentes primeiro",
            description = "`sku`, `warehouseCode` e `status` são filtros opcionais e se combinam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito informado não existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/reservations")
    @PreAuthorize("hasAuthority('ESTOQUE_RESERVATION_READ')")
    public ResponseEntity<PageResult<StockReservationResponseDTO>> listReservations(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockReservation> result = estoqueUseCase.listReservations(sku, warehouseCode,
                reservationConverter.toStatusOrNull(status), page, size);
        Map<Long, String> warehouseCodeById = new HashMap<>();
        PageResult<StockReservationResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .map(r -> reservationConverter.toResponse(r, warehouseCodeOf(r.warehouseId(), warehouseCodeById)))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consulta uma reserva de estoque")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Reserva não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/reservations/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_RESERVATION_READ')")
    public ResponseEntity<StockReservationResponseDTO> getReservation(@PathVariable Long id) {
        StockReservation reservation = estoqueUseCase.getStockReservation(id);
        String warehouseCode = estoqueUseCase.getWarehouse(reservation.warehouseId()).code();
        return ResponseEntity.ok(reservationConverter.toResponse(reservation, warehouseCode));
    }

    /**
     * Resolve o código de um depósito com cache local ao request — a listagem de reservas pode
     * atravessar vários depósitos na mesma página, e cada um só precisa ser resolvido uma vez.
     */
    private String warehouseCodeOf(Long warehouseId, Map<Long, String> cache) {
        return cache.computeIfAbsent(warehouseId, id -> estoqueUseCase.getWarehouse(id).code());
    }

    // ------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006). O `warehouseCode` da resposta é ecoado do que chegou
    // na requisição ou relido do balanço, porque o domínio guarda só o warehouseId.
    // ------------------------------------------------------------------------------------

    @Operation(summary = "Abre um balanço de inventário para um depósito",
            description = "Só pode haver um balanço aberto por depósito.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aberto", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já existe balanço aberto para o depósito", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> openStockCount(
            @Valid @RequestBody StockCountRequest request, Authentication authentication) {
        StockCount opened = estoqueUseCase.openStockCount(request.getWarehouseCode(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_COUNT_OPENED, authentication.getName(),
                Map.of("stockCountId", opened.id(), "warehouseCode", request.getWarehouseCode())));
        return ResponseEntity.created(URI.create("/estoque/stock-counts/" + opened.id()))
                .body(stockCountConverter.toResponse(opened, request.getWarehouseCode()));
    }

    @Operation(summary = "Registra a contagem física de um SKU no balanço",
            description = "Upsert por SKU: recontar sobrescreve o valor anterior. Contagem zero é válida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registrado", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Balanço ou SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Balanço não está aberto", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts/{id}/items")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> recordCountedItem(@PathVariable Long id,
            @Valid @RequestBody StockCountItemRequest request) {
        StockCount updated = estoqueUseCase.recordCountedItem(id, request.getSku(), request.getCountedQuantity(),
                request.getLotCode());
        return ResponseEntity.ok(stockCountConverter.toResponse(updated, warehouseCodeOf(updated)));
    }

    @Operation(summary = "Fecha o balanço e aplica os ajustes de saldo",
            description = "Cada SKU cuja contagem divirja do saldo do sistema gera um AJUSTE levando o saldo "
                    + "ao valor contado. Itens que bateram não geram movimentação. Tudo na mesma transação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechado", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Balanço não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Balanço não está aberto", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts/{id}/close")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> closeStockCount(@PathVariable Long id,
            Authentication authentication) {
        StockCount closed = estoqueUseCase.closeStockCount(id, authentication.getName());
        long divergentes = closed.items().stream().filter(StockCountItem::diverges).count();
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_COUNT_CLOSED, authentication.getName(),
                Map.of("stockCountId", id, "itemCount", closed.items().size(),
                        "divergentCount", divergentes)));
        return ResponseEntity.ok(stockCountConverter.toResponse(closed, warehouseCodeOf(closed)));
    }

    @Operation(summary = "Cancela o balanço sem aplicar nenhum ajuste")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelado", content = @Content(schema = @Schema(implementation = StockCountResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Balanço não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Balanço não está aberto", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/stock-counts/{id}/cancel")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> cancelStockCount(@PathVariable Long id,
            Authentication authentication) {
        StockCount cancelled = estoqueUseCase.cancelStockCount(id);
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_COUNT_CANCELLED, authentication.getName(),
                Map.of("stockCountId", id)));
        return ResponseEntity.ok(stockCountConverter.toResponse(cancelled, warehouseCodeOf(cancelled)));
    }

    @Operation(summary = "Consulta um balanço de inventário e seus itens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Balanço não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-counts/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<StockCountResponseDTO> getStockCount(@PathVariable Long id) {
        StockCount count = estoqueUseCase.getStockCount(id);
        return ResponseEntity.ok(stockCountConverter.toResponse(count, warehouseCodeOf(count)));
    }

    @Operation(summary = "Lista os balanços de um depósito, mais recentes primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/stock-counts")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<StockCountResponseDTO>> listStockCounts(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<StockCount> result = estoqueUseCase.listStockCounts(warehouseCode, page, size);
        PageResult<StockCountResponseDTO> response = new PageResult<>(
                result.content().stream().map(c -> stockCountConverter.toResponse(c, warehouseCode)).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve o código do depósito de um balanço já carregado. O domínio guarda {@code warehouseId},
     * e a API fala em {@code warehouseCode} — a tradução é responsabilidade do adapter.
     */
    private String warehouseCodeOf(StockCount count) {
        return estoqueUseCase.getWarehouse(count.warehouseId()).code();
    }

    /**
     * Diagnóstico de EST-C011. Não publica {@code AuditEvent}: é leitura, e o módulo já fixa que
     * consultas não geram evento (mesma regra de {@code GET /estoque/movements}).
     */
    @Operation(summary = "Lista pares SKU/depósito com dados de estoque cujo SKU não existe no catálogo",
            description = "Diagnóstico de integridade: levanta o passivo de saldo, movimentações e pontos de "
                    + "reposição órfãos gravados antes da validação de SKU. Somente leitura — o destino de cada "
                    + "órfão (cadastrar o produto faltante ou expurgar a linha) é decisão humana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — página vazia se a base está íntegra"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/integrity/orphan-skus")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<OrphanSkuResponseDTO>> listOrphanSkus(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<OrphanSku> result = estoqueUseCase.listOrphanSkus(page, size);
        PageResult<OrphanSkuResponseDTO> response = new PageResult<>(
                result.content().stream().map(movementConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Diagnóstico de EST-C013. Mesma razão de {@link #listOrphanSkus} para não publicar
     * {@code AuditEvent}: é leitura.
     */
    @Operation(summary = "Lista pares SKU/depósito cujo reservado no saldo diverge da soma das reservas ativas",
            description = "Diagnóstico de integridade: confronta `stock_balance.reserved_quantity` com a soma das "
                    + "reservas `ACTIVE` em `stock_reservation` para o mesmo par. Divergência aqui é estoque "
                    + "travado invisível — a venda recusa por reserva e nenhuma reserva ativa a explica (ou o "
                    + "inverso). Somente leitura — a correção de cada linha é decisão humana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — página vazia se a base está íntegra"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/integrity/reservation-mismatch")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<ReservationIntegrityMismatchResponseDTO>> listReservationMismatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<ReservationIntegrityMismatch> result = estoqueUseCase.listReservationMismatches(page, size);
        PageResult<ReservationIntegrityMismatchResponseDTO> response = new PageResult<>(
                result.content().stream().map(reservationConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * Diagnóstico de EST-F008. Mesma razão de {@link #listOrphanSkus} para não publicar
     * {@code AuditEvent}: é leitura.
     */
    @Operation(summary = "Lista pares SKU/depósito de SKU lote-rastreado cujo saldo diverge da soma dos lotes",
            description = "Diagnóstico de integridade: confronta `stock_balance.quantity` com a soma de "
                    + "`stock_lot.quantity` para o mesmo par, em SKU lote-rastreado. `stock_lot` é aditivo, sem "
                    + "FK que force a igualdade — o caminho conhecido de drift é uma SAIDA cujo FEFO não achou "
                    + "saldo suficiente nos lotes para cobrir o que o agregado já validou. Somente leitura — a "
                    + "correção de cada linha é decisão humana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — página vazia se a base está íntegra"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/integrity/lot-mismatch")
    @PreAuthorize("hasAuthority('ESTOQUE_STOCK_MANAGE')")
    public ResponseEntity<PageResult<LotIntegrityMismatchResponseDTO>> listLotMismatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<LotIntegrityMismatch> result = estoqueUseCase.listLotMismatches(page, size);
        PageResult<LotIntegrityMismatchResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }
}
