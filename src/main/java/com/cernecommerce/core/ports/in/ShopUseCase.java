package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ShopUseCase {

    /** Resultado do autocadastro: o Customer (CRM) e o User (ROLE_CUSTOMER) recém-criados. */
    record CustomerRegistration(Customer customer, User user) {
    }

    /**
     * Autocadastro do cliente do marketplace (ECM-F001, Fatia 8): cria o {@code Customer} no CRM
     * e a conta de acesso (username = email, {@code ROLE_CUSTOMER}) em uma única transação — se
     * a conta colidir com um username/email já existente (operador ou outro cliente), nada é
     * persistido.
     */
    CustomerRegistration registerCustomer(String nome, String email, String contato, String rawPassword);

    /**
     * Item resumido do catálogo público (ECM-F002) — só o que a vitrine precisa para navegar.
     * {@code available} é booleano de propósito: a vitrine não expõe quantidade exata (sinal
     * comercial de concorrente), e a checagem que vale de verdade acontece na reserva do checkout
     * (ECM-F003), não aqui.
     */
    record CatalogItem(String sku, String name, String category, BigDecimal price, boolean available,
            String imageUrl, boolean onSale, BigDecimal originalPrice, boolean superPromo) {
    }

    /** Variação de um item do catálogo, com disponibilidade própria — o preço é herdado do pai. */
    /**
     * @param price preço efetivo <b>desta</b> variação (EST-F020): o próprio, quando ela tem
     *        preço, ou o herdado do pai. Vem resolvido pelo backend para a vitrine não precisar
     *        reimplementar a precedência — e divergir dela.
     */
    record CatalogVariant(String sku, List<ProductAttribute> attributes, boolean available, BigDecimal price) {
    }

    /** Detalhe público de um item do catálogo (ECM-F002), com a grade de variações ativas. */
    record CatalogItemDetail(String sku, String name, String category, BigDecimal price, boolean available,
            List<CatalogVariant> variants, String imageUrl, boolean onSale, BigDecimal originalPrice,
            boolean superPromo, String description, String videoUrl, List<String> images) {
    }

    /**
     * Catálogo público paginado (ECM-F002, Fatia 8): só produto ativo e precificado, com preço
     * efetivo e disponibilidade no depósito padrão do marketplace ({@code
     * EstoqueUseCase#getDefaultWarehouse}).
     *
     * @param onSale filtro opcional de promoção (Estágio 01 do admin) — {@code null} não filtra;
     *        {@code true} devolve só os produtos em promoção, para a vitrine "Promoções" do
     *        marketplace (mahal-market).
     * @throws com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException
     *         se o depósito padrão do marketplace não estiver configurado
     */
    default PageResult<CatalogItem> listCatalog(int page, int size, Boolean onSale) {
        return listCatalog(page, size, onSale, null);
    }

    /**
     * @param categoryId filtro opcional de categoria — {@code null} devolve o catálogo inteiro,
     *        já na ordem da vitrine (categoria em destaque primeiro).
     */
    PageResult<CatalogItem> listCatalog(int page, int size, Boolean onSale, Long categoryId);

    /** Categorias ativas na ordem da vitrine — a primeira linha do app sai daqui. */
    java.util.List<Category> listCategories();

    /**
     * Detalhe público de um item do catálogo (ECM-F002), com a grade de variações ativas e a
     * disponibilidade de cada uma. Aceita qualquer SKU do catálogo — o do próprio pai ou o de uma
     * variação (mesma resolução de {@code EstoqueUseCase#findProductBySku}).
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir, ou existir mas estiver inativo ou sem preço — do ponto de vista do
     *         cliente, "fora de venda" e "não existe" são a mesma coisa (mesmo raciocínio de
     *         pedido de outro cliente responder 404, plano §5.4)
     * @throws com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException
     *         se o depósito padrão do marketplace não estiver configurado
     */
    CatalogItemDetail getCatalogItem(String sku);

    // ---------------------------------------------------------------------------------------
    // Carrinho e checkout (ECM-F003 + ECM-C002, Fatia 9)
    // ---------------------------------------------------------------------------------------

    /**
     * Linha do carrinho já enriquecida para exibição (plano §2.9: preço é resolvido do catálogo
     * na exibição, nunca guardado). {@code unitPrice}/{@code subtotal} nulos e {@code available}
     * falso sinalizam um SKU que não resolve mais preço — hoje inalcançável na prática (nada
     * despreço um produto depois de precificado), mas o contrato não assume isso para sempre.
     */
    record CartItemView(String sku, BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal,
            boolean available) {
    }

    /** Carrinho do cliente autenticado, pronto para exibição. */
    record CartView(List<CartItemView> items, BigDecimal total, Instant updatedAt) {
    }

    /**
     * Carrinho do cliente autenticado (username resolvido do principal — nunca de um customerId
     * do request). Carrinho nunca usado devolve uma view vazia, não erro.
     */
    CartView getCart(String username);

    /**
     * Upsert de quantidade (PUT — idempotente): define a quantidade da linha, criando o carrinho e
     * a linha se for a primeira vez. Valida o SKU contra o catálogo (existe e tem preço) antes de
     * gravar — mesma checagem que o checkout fará de novo, porque o preço nunca é guardado aqui.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir no catálogo
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException se o
     *         produto não tiver preço a cobrar
     */
    CartView upsertCartItem(String username, String sku, BigDecimal quantity);

    /**
     * Remove uma linha do carrinho.
     *
     * @throws com.cernecommerce.core.domain.exception.ecommerce.CartItemNotFoundException se o SKU
     *         não estiver no carrinho
     */
    CartView removeCartItem(String username, String sku);

    /** Pedido criado e a URL de checkout hospedada pelo gateway para onde o cliente é redirecionado. */
    record CheckoutResult(Order order, String checkoutUrl) {
    }

    /**
     * Checkout (ECM-F003 + ECM-F004): consome o carrinho do cliente autenticado inteiro, cria o
     * pedido em {@code AGUARDANDO_PAGAMENTO}, reserva o estoque de cada item no depósito padrão do
     * marketplace e cria a cobrança no gateway de pagamento, tudo na mesma transação — se a
     * reserva de qualquer item ou a criação da cobrança falhar, nada é persistido. O carrinho só é
     * esvaziado se tudo suceder.
     *
     * <p>Preço, custo e taxa de cashback são resolvidos e carimbados aqui — nunca do que estava no
     * carrinho — porque é o único jeito de a numeração e o valor cobrado corresponderem ao
     * catálogo no instante da compra, não no instante em que o item foi adicionado.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.ecommerce.CartEmptyException se o carrinho
     *         estiver vazio
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se algum
     *         SKU do carrinho não existir mais no catálogo
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException se algum
     *         item do carrinho não tiver mais preço
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se algum
     *         item não tiver disponível suficiente
     * @throws com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException
     *         se o depósito padrão do marketplace não estiver configurado
     * @throws com.cernecommerce.core.domain.exception.pagamento.PaymentGatewayException se o
     *         gateway falhar ao criar a cobrança
     */
    CheckoutResult checkout(String username);

    /** Pedidos do cliente autenticado, de qualquer canal, do mais recente para o mais antigo. */
    PageResult<Order> listMyOrders(String username, int page, int size);

    /**
     * Um pedido do cliente autenticado.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se o pedido
     *         não existir <b>ou</b> pertencer a outro cliente — as duas coisas respondem igual
     *         (404, não 403): confirmar que o pedido existe transformaria a rota num oráculo de
     *         enumeração (plano §5.4).
     */
    Order getMyOrder(String username, Long orderId);

    /**
     * Cancela um pedido do cliente autenticado (só antes de pagamento confirmado — a mesma máquina
     * de estados de {@link OrderUseCase#cancelOrder}, que faz o trabalho de verdade depois da
     * checagem de propriedade). Libera a reserva de estoque.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se o pedido
     *         não existir ou pertencer a outro cliente (mesmo raciocínio de {@link #getMyOrder})
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido já tiver pagamento confirmado
     */
    Order cancelMyOrder(String username, Long orderId, String reason);
}
