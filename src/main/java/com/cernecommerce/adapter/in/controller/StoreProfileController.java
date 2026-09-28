package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.StoreProfileRequest;
import com.cernecommerce.adapter.in.dtos.response.StoreProfileResponseDTO;
import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.ports.in.StoreProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/profile")
@Tag(name = "Store Profile", description = "Dados da loja impressos no cupom de venda")
public class StoreProfileController {

    private final StoreProfileUseCase storeProfileUseCase;

    public StoreProfileController(StoreProfileUseCase storeProfileUseCase) {
        this.storeProfileUseCase = storeProfileUseCase;
    }

    @Operation(summary = "Perfil da loja",
            description = "Livre para qualquer usuário autenticado: o PDV imprime o cabeçalho do cupom "
                    + "sem ser admin.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StoreProfileResponseDTO> get() {
        return ResponseEntity.ok(toResponse(storeProfileUseCase.get()));
    }

    @Operation(summary = "Atualiza o perfil da loja", description = "Substitui o perfil inteiro.")
    @PutMapping
    @PreAuthorize("hasAuthority('STORE_PROFILE_MANAGE')")
    public ResponseEntity<StoreProfileResponseDTO> update(@Valid @RequestBody StoreProfileRequest request,
            Authentication authentication) {
        StoreProfile profile = new StoreProfile(request.getTradeName(), request.getLegalName(),
                request.getCnpj(), request.getStateRegistration(), request.getAddressLine1(),
                request.getAddressLine2(), request.getCity(), request.getState(), request.getZipCode(),
                request.getPhone(), request.getInstagram(), request.getWebsite(), request.getLogoUrl(),
                request.getReceiptFooter());
        return ResponseEntity.ok(toResponse(storeProfileUseCase.update(profile, authentication.getName())));
    }

    private static StoreProfileResponseDTO toResponse(StoreProfile p) {
        StoreProfileResponseDTO dto = new StoreProfileResponseDTO();
        dto.setTradeName(p.tradeName());
        dto.setLegalName(p.legalName());
        dto.setCnpj(p.cnpj());
        dto.setStateRegistration(p.stateRegistration());
        dto.setAddressLine1(p.addressLine1());
        dto.setAddressLine2(p.addressLine2());
        dto.setCity(p.city());
        dto.setState(p.state());
        dto.setZipCode(p.zipCode());
        dto.setPhone(p.phone());
        dto.setInstagram(p.instagram());
        dto.setWebsite(p.website());
        dto.setLogoUrl(p.logoUrl());
        dto.setReceiptFooter(p.receiptFooter());
        return dto;
    }
}
