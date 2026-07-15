package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller stub do domínio <b>estoque</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link EstoqueUseCase} → service.
 * Endpoints previstos (TODO): CRUD de produtos/variações, ajuste de saldo,
 * importação por XML de NF-e. Autorização por permissão RBAC a definir.</p>
 */
@RestController
@RequestMapping("/estoque")
@Tag(name = "Estoque", description = "Grade de produtos e inventário — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
public class EstoqueController {

    private final EstoqueUseCase estoqueUseCase;

    public EstoqueController(EstoqueUseCase estoqueUseCase) {
        this.estoqueUseCase = estoqueUseCase;
    }

    // TODO: @PreAuthorize com permissão RBAC do domínio estoque.
    @Operation(summary = "Lista produtos (stub — retorna vazio)")
    @GetMapping("/products")
    public ResponseEntity<List<Product>> listProducts() {
        return ResponseEntity.ok(estoqueUseCase.listProducts());
    }
}
