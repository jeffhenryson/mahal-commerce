package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller stub do domínio <b>compras</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link ComprasUseCase} → service.
 * Endpoints previstos (TODO): CRUD de fornecedores, pedidos de compra, recebimento
 * de mercadorias. Autorização por permissão RBAC a definir.</p>
 */
@RestController
@RequestMapping("/compras")
@Tag(name = "Compras", description = "Fornecedores e entradas de mercadorias — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
public class ComprasController {

    private final ComprasUseCase comprasUseCase;

    public ComprasController(ComprasUseCase comprasUseCase) {
        this.comprasUseCase = comprasUseCase;
    }

    // TODO: @PreAuthorize com permissão RBAC do domínio compras.
    @Operation(summary = "Lista fornecedores (stub — retorna vazio)")
    @GetMapping("/suppliers")
    public ResponseEntity<List<Supplier>> listSuppliers() {
        return ResponseEntity.ok(comprasUseCase.listSuppliers());
    }
}
