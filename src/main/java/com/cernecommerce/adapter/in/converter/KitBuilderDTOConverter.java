package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.KitSelectionRequest;
import com.cernecommerce.adapter.in.dtos.request.KitTemplateRequest;
import com.cernecommerce.adapter.in.dtos.response.KitQuoteResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitStepOptionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.KitTemplateResponseDTO;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.ports.in.KitBuilderUseCase.StepOption;

/** EST-F031 — conversões do kit montável, compartilhadas por admin, marketplace e PDV. */
public class KitBuilderDTOConverter {

    public KitTemplate toDomain(KitTemplateRequest request) {
        return new KitTemplate(null, request.getName(), request.getDescription(), request.getImageUrl(),
                request.getDiscountPercent(),
                request.getActive() == null || request.getActive(),
                request.getVisibleInPos() == null || request.getVisibleInPos(),
                request.getVisibleInMarketplace() == null || request.getVisibleInMarketplace(),
                request.getSteps().stream()
                        .map(s -> new KitTemplateStep(s.getId(), s.getName(), s.getDisplayOrder(), s.getCategoryId(),
                                s.isRequired(), s.getMaxItems()))
                        .toList());
    }

    public KitSelection toDomain(KitSelectionRequest request) {
        return new KitSelection(request.getTemplateId(), request.getPicks().stream()
                .map(p -> new KitSelection.Pick(p.getStepId(), p.getSku()))
                .toList());
    }

    public KitTemplateResponseDTO toResponse(KitTemplate template) {
        KitTemplateResponseDTO dto = new KitTemplateResponseDTO();
        dto.setId(template.id());
        dto.setName(template.name());
        dto.setDescription(template.description());
        dto.setImageUrl(template.imageUrl());
        dto.setDiscountPercent(template.discountPercent());
        dto.setActive(template.active());
        dto.setVisibleInPos(template.visibleInPos());
        dto.setVisibleInMarketplace(template.visibleInMarketplace());
        dto.setSteps(template.steps().stream().map(s -> {
            KitTemplateResponseDTO.Step step = new KitTemplateResponseDTO.Step();
            step.setId(s.id());
            step.setName(s.name());
            step.setDisplayOrder(s.displayOrder());
            step.setCategoryId(s.categoryId());
            step.setRequired(s.required());
            step.setMaxItems(s.maxItems());
            return step;
        }).toList());
        return dto;
    }

    public KitStepOptionResponseDTO toResponse(StepOption option) {
        KitStepOptionResponseDTO dto = new KitStepOptionResponseDTO();
        dto.setSku(option.sku());
        dto.setProductSku(option.productSku());
        dto.setName(option.name());
        dto.setAttributes(option.attributes());
        dto.setPrice(option.price());
        dto.setImageUrl(option.imageUrl());
        dto.setAvailable(option.available());
        return dto;
    }

    public KitQuoteResponseDTO toResponse(KitQuote quote) {
        KitQuoteResponseDTO dto = new KitQuoteResponseDTO();
        dto.setTemplateId(quote.template().id());
        dto.setTemplateName(quote.template().name());
        dto.setDiscountPercent(quote.template().discountPercent());
        dto.setSubtotal(quote.subtotal());
        dto.setDiscount(quote.discount());
        dto.setTotal(quote.total());
        dto.setLines(quote.lines().stream().map(l -> {
            KitQuoteResponseDTO.Line line = new KitQuoteResponseDTO.Line();
            line.setStepId(l.stepId());
            line.setStepName(l.stepName());
            line.setSku(l.sku());
            line.setProductName(l.productName());
            line.setUnitPrice(l.unitPrice());
            line.setDiscountAmount(l.discountAmount());
            line.setNetAmount(l.netAmount());
            return line;
        }).toList());
        return dto;
    }
}
