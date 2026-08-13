package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.KitComponentRequest;
import com.cernecommerce.adapter.in.dtos.request.KitRecipeRequest;
import com.cernecommerce.adapter.in.dtos.request.PricingRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductAttributeRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductVariantRequest;
import com.cernecommerce.adapter.in.dtos.response.EstoqueSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitComponentResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.LotIntegrityMismatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PricingResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductAttributeResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductVariantResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockLotResponseDTO;
import com.cernecommerce.core.domain.model.estoque.EstoqueSummary;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.ports.in.EstoqueUseCase.KitComponentCommand;

import java.util.List;

public class ProductDTOConverter {

    public List<ProductVariant> toVariants(List<ProductVariantRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> ProductVariant.create(r.getSku(), toAttributes(r.getAttributes()),
                        toPricing(r.getPricing()), r.getBarcode()))
                .toList();
    }

    /**
     * Converte atributos do request. Usado tanto para os da variação quanto para os do próprio
     * produto — é o mesmo DTO e o mesmo tipo de domínio, só muda onde ficam pendurados.
     *
     * <p>Público porque o controller precisa distinguir "não veio" de "veio vazio" no PATCH: aqui
     * ausência vira lista vazia, e quem precisa do {@code null} de "não mexer" trata antes de
     * chamar.</p>
     */
    public List<ProductAttribute> toAttributes(List<ProductAttributeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> new ProductAttribute(r.getType(), r.getValue()))
                .toList();
    }

    /**
     * Converte o bloco de precificação do request (EST-F019). Retorna {@code null} quando o
     * bloco não veio — e é justamente esse {@code null} que o {@code updateProduct} lê como
     * "não mexer no preço".
     */
    public Pricing toPricing(PricingRequest request) {
        if (request == null) {
            return null;
        }
        return Pricing.of(request.getCostPrice(), request.getMarkupPercent(), request.getSalePrice(),
                request.getOriginalPrice(), request.getCauseAmount());
    }

    public ProductResponseDTO toResponse(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.id());
        dto.setSku(product.sku());
        dto.setName(product.name());
        dto.setCategory(product.category());
        dto.setCategoryId(product.categoryId());
        dto.setBrand(product.brand());
        dto.setImageUrl(product.imageUrl());
        dto.setOnSale(product.onSale());
        dto.setSuperPromo(product.superPromo());
        dto.setDescription(product.description());
        dto.setVideoUrl(product.videoUrl());
        dto.setImages(product.images());
        dto.setAttributes(product.attributes().stream().map(this::toResponse).toList());
        dto.setActive(product.active());
        dto.setVariants(product.variants().stream().map(this::toResponse).toList());
        dto.setPricing(toResponse(product.pricing()));
        dto.setType(product.type().name());
        dto.setLotTracked(product.lotTracked());
        dto.setBarcode(product.barcode());
        dto.setUnit(product.unit().name());
        dto.setSampleProduct(product.sampleProduct());
        dto.setKitComponentEligible(product.kitComponentEligible());
        dto.setVisibleInPos(product.visibleInPos());
        dto.setVisibleInMarketplace(product.visibleInMarketplace());
        return dto;
    }

    /** Converte a receita do request (EST-F015) no comando que o use case espera. */
    public List<KitComponentCommand> toKitComponentCommands(KitRecipeRequest request) {
        return request.getComponents().stream()
                .map(c -> new KitComponentCommand(c.getComponentSku(), c.getQuantity()))
                .toList();
    }

    public KitComponentResponseDTO toKitComponentResponse(KitComponent component) {
        KitComponentResponseDTO dto = new KitComponentResponseDTO();
        dto.setComponentSku(component.componentSku());
        dto.setQuantity(component.quantity());
        return dto;
    }

    /** Serializa a precificação com os derivados já calculados pelo domínio. */
    public PricingResponseDTO toResponse(Pricing pricing) {
        PricingResponseDTO dto = new PricingResponseDTO();
        dto.setCostPrice(pricing.costPrice());
        dto.setMarkupPercent(pricing.markupPercent());
        dto.setSalePrice(pricing.salePrice());
        dto.setSuggestedPrice(pricing.suggestedPrice());
        dto.setEffectivePrice(pricing.effectivePrice());
        dto.setMarginAmount(pricing.marginAmount());
        dto.setMarginPercent(pricing.marginPercent());
        dto.setEffectiveMarkupPercent(pricing.effectiveMarkupPercent());
        dto.setPriced(pricing.isPriced());
        dto.setBelowCost(pricing.isBelowCost());
        dto.setOriginalPrice(pricing.originalPrice());
        dto.setHasDiscount(pricing.hasDiscount());
        dto.setDiscountPercent(pricing.discountPercent());
        dto.setCauseAmount(pricing.causeAmount());
        return dto;
    }

    private ProductVariantResponseDTO toResponse(ProductVariant variant) {
        ProductVariantResponseDTO dto = new ProductVariantResponseDTO();
        dto.setId(variant.id());
        dto.setSku(variant.sku());
        dto.setActive(variant.active());
        dto.setAttributes(variant.attributes().stream().map(this::toResponse).toList());
        // Nulo quando a variação herda do pai — a ausência do bloco é o que sinaliza a herança.
        dto.setPricing(variant.pricing() == null ? null : toResponse(variant.pricing()));
        dto.setBarcode(variant.barcode());
        return dto;
    }

    private ProductAttributeResponseDTO toResponse(ProductAttribute attribute) {
        ProductAttributeResponseDTO dto = new ProductAttributeResponseDTO();
        dto.setType(attribute.type());
        dto.setValue(attribute.value());
        return dto;
    }

    /**
     * @param warehouseCode resolvido pelo controller — o domínio guarda {@code warehouseId}, e a
     *        API fala em código de depósito em toda parte (EST-F008)
     */
    public StockLotResponseDTO toResponse(StockLot lot, String warehouseCode) {
        StockLotResponseDTO dto = new StockLotResponseDTO();
        dto.setId(lot.id());
        dto.setSku(lot.sku());
        dto.setWarehouseCode(warehouseCode);
        dto.setLotCode(lot.lotCode());
        dto.setExpiryDate(lot.expiryDate());
        dto.setQuantity(lot.quantity());
        dto.setAlertedAt(lot.alertedAt());
        return dto;
    }

    public EstoqueSummaryResponseDTO toResponse(EstoqueSummary summary) {
        EstoqueSummaryResponseDTO dto = new EstoqueSummaryResponseDTO();
        dto.setTotalProdutos(summary.totalProdutos());
        dto.setTotalVariantes(summary.totalVariantes());
        dto.setValorEstoqueCusto(summary.valorEstoqueCusto());
        dto.setAlertasCriticos(summary.alertasCriticos());
        dto.setAlertasAtencao(summary.alertasAtencao());
        if (summary.categoriaComMaisProdutos() != null) {
            EstoqueSummaryResponseDTO.CategoriaComMaisProdutosDTO categoria =
                    new EstoqueSummaryResponseDTO.CategoriaComMaisProdutosDTO();
            categoria.setNome(summary.categoriaComMaisProdutos().name());
            categoria.setQuantidade(summary.categoriaComMaisProdutos().count());
            dto.setCategoriaComMaisProdutos(categoria);
        }
        return dto;
    }

    public LotIntegrityMismatchResponseDTO toResponse(LotIntegrityMismatch mismatch) {
        LotIntegrityMismatchResponseDTO dto = new LotIntegrityMismatchResponseDTO();
        dto.setSku(mismatch.sku());
        dto.setWarehouseCode(mismatch.warehouseCode());
        dto.setBalanceQuantity(mismatch.balanceQuantity());
        dto.setLotsTotal(mismatch.lotsTotal());
        dto.setDifference(mismatch.difference());
        return dto;
    }
}
