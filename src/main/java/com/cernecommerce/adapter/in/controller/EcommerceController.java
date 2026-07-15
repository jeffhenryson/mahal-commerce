package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.ports.in.EcommerceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller stub do domínio <b>ecommerce</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link EcommerceUseCase} → service.
 * Endpoints previstos (TODO): carrinho, cupons, promoções, checkout/pagamentos.
 * Autorização por permissão RBAC a definir.</p>
 */
@RestController
@RequestMapping("/ecommerce")
@Tag(name = "E-commerce", description = "Carrinho, cupons e promoções — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
public class EcommerceController {

    private final EcommerceUseCase ecommerceUseCase;

    public EcommerceController(EcommerceUseCase ecommerceUseCase) {
        this.ecommerceUseCase = ecommerceUseCase;
    }

    // TODO: @PreAuthorize com permissão RBAC do domínio ecommerce.
    @Operation(summary = "Lista carrinhos (stub — retorna vazio)")
    @GetMapping("/carts")
    public ResponseEntity<List<Cart>> listCarts() {
        return ResponseEntity.ok(ecommerceUseCase.listCarts());
    }
}
