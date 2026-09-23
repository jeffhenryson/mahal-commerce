package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.KitBuilderDTOConverter;
import com.cernecommerce.adapter.in.converter.ShopCartDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.KitSelectionRequest;
import com.cernecommerce.adapter.in.dtos.response.ShopCartResponseDTO;
import com.cernecommerce.core.ports.in.ShopUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kit montável no carrinho do cliente autenticado (ECM-F008). Mesma permissão do carrinho avulso. */
@RestController
@RequestMapping("/shop/cart/kits")
@Tag(name = "Marketplace (Kit montável)", description = "Montador do Kit Mahal na vitrine")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ShopKitCartController {

    private final ShopUseCase shopUseCase;
    private final ShopCartDTOConverter cartConverter;
    private final KitBuilderDTOConverter kitConverter;

    public ShopKitCartController(ShopUseCase shopUseCase, ShopCartDTOConverter cartConverter,
            KitBuilderDTOConverter kitConverter) {
        this.shopUseCase = shopUseCase;
        this.cartConverter = cartConverter;
        this.kitConverter = kitConverter;
    }

    @Operation(summary = "Põe um kit montado no carrinho",
            description = "Cada chamada é um pacote novo (`kitBundleId`), mesmo que igual a um que já está "
                    + "no carrinho. O preço não é guardado: o checkout recota o kit e recusa o pedido se "
                    + "a escolha deixou de valer.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Carrinho atualizado"),
            @ApiResponse(responseCode = "422", description = "Escolha não fecha um kit válido — ver `code`", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('SHOP_CART_OWN')")
    public ResponseEntity<ShopCartResponseDTO> addKit(@Valid @RequestBody KitSelectionRequest request,
            Authentication authentication) {
        ShopUseCase.CartView cart = shopUseCase.addKitToCart(authentication.getName(), kitConverter.toDomain(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(cartConverter.toResponse(cart));
    }

    @Operation(summary = "Tira um kit inteiro do carrinho",
            description = "Item de kit não sai sozinho: sem ele o desconto cotado deixaria de valer.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "404", description = "Kit não estava no carrinho", content = @Content)
    })
    @DeleteMapping("/{bundleId}")
    @PreAuthorize("hasAuthority('SHOP_CART_OWN')")
    public ResponseEntity<Void> removeKit(@PathVariable String bundleId, Authentication authentication) {
        shopUseCase.removeKitFromCart(authentication.getName(), bundleId);
        return ResponseEntity.noContent().build();
    }
}
