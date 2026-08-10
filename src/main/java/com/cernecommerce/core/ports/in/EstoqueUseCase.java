package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port de entrada do domínio <b>estoque</b>.
 */
public interface EstoqueUseCase {

    /**
     * Cria um produto (SKU pai) com suas variações. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException}
     * se o SKU do produto ou de alguma variação já existir.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants) {
        return createProduct(sku, name, category, variants, Pricing.empty());
    }

    /**
     * Cria um produto precificado (EST-F019). {@code pricing} nulo equivale a
     * {@link Pricing#empty()} — produto sem preço é estado válido do catálogo.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing) {
        return createProduct(sku, name, category, variants, pricing, null);
    }

    /** Cria um produto com marca, sem imagem cadastrada nem promoção (EST-F0xx). */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand) {
        return createProduct(sku, name, category, variants, pricing, brand, null, false);
    }

    /**
     * Cria um produto com marca, imagem cadastrada manualmente e sinalização de promoção
     * (Estágio 01 do admin), sem os campos de marketing adicionados depois (super promo,
     * descrição, vídeo, galeria).
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, false, null, null,
                List.of());
    }

    /**
     * Cria um produto (forma canônica) com marca, imagem, promoção, selo de super promoção,
     * descrição, vídeo e galeria de imagens — nenhum desses campos tem regra de negócio, só
     * persiste/retorna.
     */
    default Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing, String brand, String imageUrl, boolean onSale, boolean superPromo, String description,
            String videoUrl, List<String> images) {
        return createProduct(sku, name, category, variants, pricing, brand, imageUrl, onSale, superPromo, description,
                videoUrl, images, List.of());
    }

    /**
     * Cria um produto (forma canônica) incluindo os atributos descritivos do próprio SKU pai.
     *
     * <p>Distintos dos atributos de {@code variants}: aqueles fazem parte do que <i>identifica</i>
     * cada variação da grade, estes só <i>descrevem</i> o item e existem para o produto sem grade,
     * que não tinha onde carregá-los.</p>
     */
    Product createProduct(String sku, String name, String category, List<ProductVariant> variants, Pricing pricing,
            String brand, String imageUrl, boolean onSale, boolean superPromo, String description, String videoUrl,
            List<String> images, List<ProductAttribute> attributes);

    /** Lista produtos paginados, sem filtro e ordenados por id. */
    default PageResult<Product> listProducts(int page, int size) {
        return listProducts(page, size, ProductFilter.EMPTY, ProductSortField.ID, SortDirection.ASC);
    }

    /**
     * Lista produtos paginados aplicando busca, filtros e ordenação <b>no banco</b>.
     *
     * <p>Existe porque a versão só-paginada obrigava o cliente a baixar o catálogo inteiro para
     * filtrar em memória. Como o teto de {@code size} é 100 (EST-C005), catálogo maior que isso
     * fazia busca, KPI e exportação do admin passarem a mentir em silêncio: os 100 primeiros
     * sempre voltam, então não há erro visível — só resultado incompleto.</p>
     *
     * @param filter critérios opcionais; {@link ProductFilter#EMPTY} não filtra nada.
     * @param sortField campo de ordenação, sempre desempatado por id (EST-C012).
     */
    PageResult<Product> listProducts(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction);

    /**
     * Produtos ativos e precificados, paginados — a consulta que o catálogo público consome
     * (ECM-F002). Filtra no banco, não em memória: paginar depois de filtrar em memória devolveria
     * contagem de página errada.
     *
     * @param onSale filtro opcional de promoção (Estágio 01 do admin) — {@code null} não filtra.
     */
    PageResult<Product> listActivePricedProducts(int page, int size, Boolean onSale);

    /**
     * Alteração parcial de produto (EST-F018): {@code name} e/ou {@code category} nulos são
     * mantidos como estão. Não altera {@code sku} (identidade referenciada como texto livre pelas
     * tabelas de estoque) nem as variações. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * não for um SKU pai existente.
     */
    default Product updateProduct(String sku, String name, String category) {
        return updateProduct(sku, name, category, null);
    }

    /**
     * Alteração parcial incluindo precificação (EST-F019). {@code pricing} nulo mantém a
     * precificação atual; se vier preenchido, cada um dos seus três campos segue a mesma
     * semântica de PATCH — nulo mantém, valor troca (ver {@link Pricing#withPatch}).
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing) {
        return updateProduct(sku, name, category, pricing, null);
    }

    /**
     * Alteração parcial incluindo marca, sem mexer em imagem ou promoção. {@code brand} nulo
     * mantém a marca atual — mesma semântica de PATCH de {@code name}/{@code category}.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand) {
        return updateProduct(sku, name, category, pricing, brand, null, null);
    }

    /**
     * Alteração parcial incluindo imagem cadastrada manualmente e sinalização de promoção
     * (Estágio 01 do admin), sem os campos de marketing adicionados depois (super promo,
     * descrição, vídeo, galeria). {@code imageUrl} nulo mantém a atual — mesma semântica de
     * {@code brand}. {@code onSale} nulo mantém a sinalização atual; {@code true}/{@code false}
     * explícitos trocam — é um toggle, não um "nulo mantém" de String.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, null, null, null, null);
    }

    /**
     * Alteração parcial (forma canônica), incluindo selo de super promoção, descrição, vídeo e
     * galeria de imagens. {@code superPromo} segue a mesma semântica de toggle de {@code onSale}
     * (nulo mantém, valor explícito troca). {@code description}/{@code videoUrl} nulos mantêm o
     * valor atual — mesma semântica de {@code brand}/{@code imageUrl}. {@code images} nulo
     * mantém a galeria atual; uma lista (mesmo vazia) substitui a galeria inteira — sem edição
     * parcial de item individual.
     */
    default Product updateProduct(String sku, String name, String category, Pricing pricing, String brand,
            String imageUrl, Boolean onSale, Boolean superPromo, String description, String videoUrl,
            List<String> images) {
        return updateProduct(sku, name, category, pricing, brand, imageUrl, onSale, superPromo, description, videoUrl,
                images, null);
    }

    /**
     * Alteração parcial (forma canônica) incluindo os atributos do próprio SKU pai.
     * {@code attributes} nulo mantém os atuais; uma lista (mesmo vazia) substitui o conjunto
     * inteiro — a mesma semântica já adotada para {@code images}, e pelo mesmo motivo: não há
     * identidade estável por atributo que permitisse edição item a item.
     */
    Product updateProduct(String sku, String name, String category, Pricing pricing, String brand, String imageUrl,
            Boolean onSale, Boolean superPromo, String description, String videoUrl, List<String> images,
            List<ProductAttribute> attributes);

    /**
     * Resolve a precificação vigente de <b>qualquer</b> SKU do catálogo — pai ou variação
     * (EST-F019). Variação herda o preço do pai. É a consulta que o PDV e a vitrine usam antes
     * de montar o item de venda.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException
     *         se o SKU não existir no catálogo.
     */
    Pricing findPricingBySku(String sku);

    /**
     * Resolve o produto (com categoria e precificação) de qualquer SKU do catálogo — pai ou
     * variação. Usado, por exemplo, pela cadeia de resolução de taxa de cashback (CRM-F003), que
     * precisa da categoria do produto, não só do preço.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException
     *         se o SKU não existir no catálogo.
     */
    Product findProductBySku(String sku);

    /**
     * Ativa ou desativa um produto (EST-F018). Produto inativo <b>recusa entrada</b> de estoque
     * — manual ou por recebimento de Compras —, mas continua aceitando saída, para escoar o saldo
     * remanescente. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * pai não existir.
     */
    Product setProductActive(String sku, boolean active);

    /**
     * Ativa ou desativa o rastreamento de lote e validade de um produto (EST-F008) — opt-in por
     * SKU, já que só essência/carvão/perecível faz sentido rastrear. A partir daqui,
     * {@link #adjustStock(String, String, MovementType, BigDecimal, String, String, String, LocalDate)}
     * passa a exigir lote em toda {@code ENTRADA} deste SKU. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * pai não existir, ou {@link IllegalArgumentException} se o SKU for um {@code KIT} — kit não
     * tem saldo físico próprio para rastrear.
     */
    Product setProductLotTracked(String sku, boolean lotTracked);

    /**
     * Alteração parcial de depósito (EST-F018): {@code name} e/ou {@code type} nulos são mantidos.
     * Não altera {@code code}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse updateWarehouse(String code, String name, WarehouseType type);

    /**
     * Ativa ou desativa um depósito (EST-F018). Mesma regra do produto: para de receber entrada,
     * continua despachando saída.
     */
    Warehouse setWarehouseActive(String code, boolean active);

    /**
     * Cria um depósito (loja física ou e-commerce). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException}
     * se o código já existir.
     */
    Warehouse createWarehouse(String code, String name, WarehouseType type);

    /** Lista todos os depósitos cadastrados. */
    /** Lista depósitos paginados, ordenados por id. */
    PageResult<Warehouse> listWarehouses(int page, int size);

    /**
     * Busca um depósito por id. Existe para o adapter traduzir o {@code warehouseId} que os
     * modelos guardam no {@code warehouseCode} que a API expõe — caso do balanço de inventário,
     * consultado por id e sem código na requisição. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse getWarehouse(Long warehouseId);

    /**
     * Busca um depósito por código. Existe para quem precisa <b>validar</b> um código antes de
     * guardá-lo — caso da abertura de caixa (PDV-F001), que carimba o depósito na sessão e não pode
     * descobrir na primeira venda que ele não existe. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse getWarehouseByCode(String code);

    /**
     * Depósito padrão do marketplace (ECM-F002/F003, plano §2.2): a operação de hoje tem um
     * depósito físico só, e a superfície pública (catálogo, e futuramente checkout) não tem
     * sessão de operador para informar um {@code warehouseCode} explícito. Resolvido a partir de
     * {@code system_config.estoque.warehouse.default-code}.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException
     *         se a chave não estiver configurada (ausente ou em branco)
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         código configurado não corresponder a nenhum depósito existente
     */
    Warehouse getDefaultWarehouse();

    /**
     * Consulta o saldo de um SKU em um depósito. Retorna saldo zero se ainda não houve
     * nenhuma movimentação para o par SKU/depósito. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    StockBalance getStockBalance(String sku, String warehouseCode);

    /**
     * Saldo paginado de todos os produtos de um depósito, ordenado por SKU — alimenta telas
     * de alertas de reposição que precisam comparar o saldo de cada produto contra o seu ponto
     * de reposição. Não inclui KITs: eles não têm linha própria em {@code stock_balance} (o
     * saldo é derivado ao vivo só na consulta por 1 SKU, ver {@link #getStockBalance}). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    PageResult<StockBalance> listStockBalances(String warehouseCode, int page, int size);

    /**
     * Registra uma movimentação manual de estoque (entrada, saída ou ajuste) e atualiza o
     * {@link StockBalance} correspondente na mesma transação. Retorna o saldo atualizado.
     * Lança {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o
     * SKU não existir no catálogo (nem como SKU pai, nem como SKU de variação),
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.InsufficientStockException} se
     * uma SAIDA deixaria o saldo negativo.
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username);

    /**
     * Mesmo que {@link #adjustStock(String, String, MovementType, BigDecimal, String, String)}, com
     * lote e validade explícitos (EST-F008) — só se aplica a {@code ENTRADA} de SKU
     * {@link com.cernecommerce.core.domain.model.estoque.Product#lotTracked()}.
     * {@code SAIDA} não recebe lote: o consumo é automático por FEFO (do que vence primeiro em
     * diante), entre os lotes já recebidos.
     *
     * @param lotCode identificador do lote recebido; obrigatório junto com {@code expiryDate}
     *        quando o SKU é lote-rastreado e o tipo é {@code ENTRADA}.
     * @param expiryDate validade do lote. Se o {@code lotCode} já existir para o par SKU/depósito
     *        com outra validade gravada, a chamada falha — o mesmo lote não muda de validade.
     * @throws com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException se o SKU é
     *         lote-rastreado, o tipo é {@code ENTRADA} e {@code lotCode}/{@code expiryDate} não
     *         vierem preenchidos
     * @throws com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException se
     *         {@code lotCode}/{@code expiryDate} vierem preenchidos para um SKU não lote-rastreado,
     *         ou para um tipo diferente de {@code ENTRADA}
     * @throws com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException se o
     *         {@code lotCode} já existir com outra validade
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate);

    /**
     * Lista os lotes de um SKU num depósito (EST-F008), do que vence primeiro em diante. Lista
     * vazia se o SKU não é lote-rastreado ou ainda não recebeu nenhum lote — não é erro, é o
     * estado normal de todo SKU antes do primeiro {@code adjustStock(ENTRADA)} com lote.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         código do depósito não existir
     */
    List<StockLot> listStockLots(String sku, String warehouseCode);

    /**
     * Histórico paginado de movimentações, mais recentes primeiro. {@code sku} e
     * {@code warehouseCode} são opcionais — omitidos, o feed traz todas as movimentações; quando
     * informados, filtram por esse SKU e/ou depósito. Retorna página vazia se o filtro nunca foi
     * movimentado. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito informado não existir.
     */
    PageResult<StockMovement> listMovements(String sku, String warehouseCode, int page, int size);

    /**
     * Define (cria ou atualiza) o ponto de reposição de um SKU em um depósito. A partir dessa
     * chamada, toda movimentação que deixe o saldo abaixo de {@code minQuantity} dispara uma
     * notificação para os usuários com permissão {@code ESTOQUE_STOCK_MANAGE}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * não existir no catálogo, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    void setReorderPoint(String sku, String warehouseCode, BigDecimal minQuantity);

    /**
     * Consulta o ponto de reposição de um SKU num depósito. Vazio se não houver nenhum
     * configurado — leitura não exige que o SKU já exista no catálogo (mesma leniência de
     * {@link #getStockBalance}). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    Optional<ReorderPoint> getReorderPoint(String sku, String warehouseCode);

    /**
     * Pontos de reposição configurados num depósito, paginados, ordenados por SKU — alimenta
     * telas de alertas de reposição junto com {@link #listStockBalances}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    PageResult<ReorderPoint> listReorderPoints(String warehouseCode, int page, int size);

    /**
     * Diagnóstico de integridade (EST-C011): pares SKU/depósito com saldo, movimentação ou ponto
     * de reposição gravados cujo SKU não existe no catálogo, nem como SKU pai nem como SKU de
     * variação.
     *
     * <p>Desde EST-C002 nenhuma escrita nova cria um órfão — {@code adjustStock} e
     * {@code setReorderPoint} barram SKU desconhecido. O que esta consulta levanta é o passivo
     * anterior àquela correção, que continua na base porque não há FK das tabelas de estoque
     * para {@code product}.</p>
     *
     * <p><b>Somente leitura, e de propósito:</b> o destino de cada órfão — cadastrar o produto
     * que falta ou expurgar a linha — é decisão humana. Expurgar em massa arriscaria apagar
     * histórico legítimo, então não existe operação de limpeza automática.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<OrphanSku> listOrphanSkus(int page, int size);

    /**
     * Diagnóstico de integridade da reserva (EST-C013): pares SKU/depósito cujo
     * {@code stock_balance.reserved_quantity} diverge da soma das reservas {@code ACTIVE} no
     * ledger {@code stock_reservation}.
     *
     * <p>Diferente do órfão de SKU, essa divergência é <b>estoque travado invisível</b>: a venda
     * recusa por reserva e não há reserva ativa que a explique (ou o inverso). Somente leitura —
     * a correção é decisão humana.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<ReservationIntegrityMismatch> listReservationMismatches(int page, int size);

    /**
     * Diagnóstico de integridade de lote (EST-F008): pares SKU/depósito de SKU lote-rastreado cujo
     * {@code stock_balance.quantity} diverge da soma de {@code stock_lot.quantity} para o mesmo
     * par.
     *
     * <p>{@code stock_lot} é aditivo (ver javadoc de {@code StockLot}) — mantido pela mesma
     * transação de {@code adjustStock}, mas sem FK que force a igualdade. O caminho conhecido de
     * drift é {@code consumeLotsFefo} não achar saldo suficiente nos lotes para cobrir uma SAIDA já
     * validada contra o agregado (comentário em {@code EstoqueService}) — aqui é onde esse drift
     * fica visível, em vez de silencioso. Somente leitura — a correção é decisão humana.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<LotIntegrityMismatch> listLotMismatches(int page, int size);

    // ---------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006)
    // ---------------------------------------------------------------------------------------

    /**
     * Abre um balanço para o depósito. Só pode haver <b>um aberto por depósito</b>: dois
     * simultâneos sobre o mesmo saldo se sobrescreveriam no fechamento. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} ou
     * {@link IllegalStateException} se já houver um aberto.
     */
    StockCount openStockCount(String warehouseCode, String username);

    /**
     * Registra o que foi contado de um SKU não lote-rastreado. É upsert por SKU — recontar
     * sobrescreve. Exige balanço {@link com.cernecommerce.core.domain.model.estoque.StockCountStatus#ABERTA}
     * e SKU existente no catálogo.
     *
     * <p>Equivale a {@link #recordCountedItem(Long, String, BigDecimal, String)} com
     * {@code lotCode = null} — falha se o SKU for lote-rastreado (EST-F008), porque nesse caso a
     * contagem precisa dizer qual lote.</p>
     */
    StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity);

    /**
     * Mesmo que {@link #recordCountedItem(Long, String, BigDecimal)}, com o lote contado explícito
     * (EST-F008). SKU lote-rastreado exige {@code lotCode}; SKU não lote-rastreado recusa se vier
     * preenchido. É upsert por {@code (sku, lotCode)} — recontar o mesmo lote sobrescreve, contar
     * um lote diferente do mesmo SKU acrescenta uma linha.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException se o SKU for
     *         lote-rastreado e {@code lotCode} vier nulo/vazio
     * @throws com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException se o SKU
     *         não for lote-rastreado e {@code lotCode} vier preenchido
     * @throws com.cernecommerce.core.domain.exception.estoque.StockLotNotFoundException se o
     *         {@code lotCode} não corresponder a nenhum lote existente — a contagem só reconcilia
     *         lote que já existe; lote novo entra por recebimento, não pelo balanço
     */
    StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity, String lotCode);

    /**
     * Fecha o balanço e aplica os ajustes: para cada item cuja contagem <b>divirja</b> do saldo do
     * sistema, grava um {@link MovementType#AJUSTE} levando o saldo ao valor contado. Item que
     * bateu não gera movimentação — contagem certa não polui o ledger.
     *
     * <p>SKU lote-rastreado (EST-F008) é reconciliado por lote primeiro — cada {@code StockLot}
     * contado recebe seu próprio {@code reconciledTo}, para {@code SUM(stock_lot.quantity)}
     * continuar igual a {@code stock_balance.quantity} — e só então o agregado recebe UM
     * {@code AJUSTE} para a soma dos lotes, no mesmo formato de ledger do SKU não lote-rastreado.</p>
     *
     * <p>Tudo na mesma transação: se um SKU falhar, nenhum ajuste é aplicado e o balanço continua
     * aberto. Os itens ficam com {@code expectedQuantity} e {@code difference} carimbados, que é o
     * registro auditável da divergência.</p>
     */
    StockCount closeStockCount(Long stockCountId, String username);

    /** Abandona o balanço sem tocar em saldo nenhum. Exige que esteja aberto. */
    StockCount cancelStockCount(Long stockCountId);

    /** Consulta um balanço com seus itens. */
    StockCount getStockCount(Long stockCountId);

    /** Balanços de um depósito, dos mais recentes para os mais antigos. */
    PageResult<StockCount> listStockCounts(String warehouseCode, int page, int size);

    // ---------------------------------------------------------------------------------------
    // Reserva de estoque (EST-F021)
    // ---------------------------------------------------------------------------------------

    /**
     * Compromete saldo de um SKU sem tirá-lo da prateleira, para que balcão e marketplace consumam
     * o mesmo depósito sem overselling. É o caminho do <b>checkout online</b>: entre montar o
     * pedido e o pagamento confirmar, o saldo precisa estar prometido sem ter saído.
     *
     * <p>O PDV <b>não</b> usa este caminho — no balcão a mercadoria sai na hora, e
     * {@link #adjustStock} com {@code SAIDA} continua sendo o correto.</p>
     *
     * @param ownerReference identificador de quem pediu a reserva, usado depois para consumir ou
     *        liberar o conjunto todo. Obrigatório.
     * @param ttl validade da reserva; {@code null} usa o padrão configurado. Reserva sem prazo
     *        seria saldo perdido sem ninguém perceber, então não há como criar uma perpétua.
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU não
     *         existir no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se o
     *         depósito não existir
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException se o
     *         <b>disponível</b> não cobrir a quantidade
     */
    StockReservation reserveStock(String sku, String warehouseCode, BigDecimal quantity,
            String ownerReference, Duration ttl, String username);

    /**
     * Converte a reserva em saída de verdade: grava um {@link MovementType#SAIDA} no ledger e baixa
     * o físico e o reservado juntos. O disponível não se mexe — já estava descontado desde a
     * reserva. É o que o webhook de pagamento confirmado chama.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException
     *         se a reserva não existir
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException
     *         se já tiver sido consumida, liberada ou expirada — consumir duas vezes daria baixa
     *         dobrada na mesma mercadoria
     */
    StockReservation consumeReservation(Long reservationId, String username);

    /**
     * Devolve a reserva ao disponível sem mexer no físico — pedido cancelado ou carrinho desfeito.
     * Não gera movimentação: nada entrou nem saiu da prateleira.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException
     *         se a reserva não existir
     * @throws com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException
     *         se já estiver resolvida
     */
    StockReservation releaseReservation(Long reservationId, String username);

    /**
     * Libera de uma vez todas as reservas ativas de um dono. É a operação que o cancelamento de um
     * pedido com vários itens usa, para não depender de o chamador ter guardado id por id.
     *
     * @return quantas reservas foram liberadas; zero se não havia nenhuma ativa (idempotente de
     *         propósito — cancelar um pedido duas vezes não é erro)
     */
    int releaseReservationsByOwner(String ownerReference, String username);

    /**
     * Converte de uma vez todas as reservas ativas de um dono em saída real — o gêmeo de
     * {@link #releaseReservationsByOwner} para o caminho feliz. É o que a confirmação de pagamento
     * de um pedido com vários itens usa, inclusive quando esse pagamento acontece no balcão.
     *
     * <p>O físico e o reservado caem juntos; o disponível não se mexe, porque já estava descontado
     * desde a reserva.</p>
     *
     * @return quantas reservas foram consumidas; zero se não havia nenhuma ativa
     */
    int consumeReservationsByOwner(String ownerReference, String username);

    /** Consulta uma reserva. */
    StockReservation getStockReservation(Long reservationId);

    /**
     * Listagem filtrada de reservas, mais recentes primeiro. {@code sku}, {@code warehouseCode} e
     * {@code status} são opcionais e se combinam.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException se
     *         {@code warehouseCode} for informado e não existir
     */
    PageResult<StockReservation> listReservations(String sku, String warehouseCode, ReservationStatus status,
            int page, int size);

    /**
     * Expira as reservas vencidas, devolvendo a quantidade ao disponível. Chamado pelo varredor
     * agendado, em lotes.
     *
     * @return quantas reservas foram expiradas nesta passada
     */
    int expireReservations(int batchSize);

    /**
     * Notifica quem tem {@code ESTOQUE_STOCK_MANAGE} sobre todo {@link
     * com.cernecommerce.core.domain.model.estoque.StockLot} que vence em {@code cutoffDays} dias
     * ou menos e ainda não foi alertado (EST-F008). Chamado pelo varredor agendado, em lotes —
     * cada lote alertado é carimbado ({@code StockLot#alerted()}) para o próximo run não repetir.
     *
     * @return quantos lotes foram alertados nesta passada
     */
    int alertExpiringLots(int cutoffDays, int batchSize);

    // ---------------------------------------------------------------------------------------
    // Kits (EST-F015) — virtuais, de um nível só (§2.10 do plano)
    // ---------------------------------------------------------------------------------------

    /** O que o chamador informa por linha de receita: SKU do componente e quantidade consumida. */
    record KitComponentCommand(String componentSku, BigDecimal quantity) {
    }

    /**
     * Define (substitui integralmente) a receita de um kit e promove o produto a {@code KIT}
     * como efeito colateral — não é preciso chamar nada além disto para um SKU virar kit.
     *
     * <p>PUT idempotente: chamar de novo com uma lista diferente substitui a receita inteira, não
     * mescla. Componente precisa ser {@code SIMPLES} — kit dentro de kit é proibido por
     * construção, não detectado por travessia.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se
     *         {@code kitSku} não existir como SKU pai, ou se algum {@code componentSku} não
     *         existir no catálogo
     * @throws com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException se
     *         {@code components} vier vazio
     * @throws com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException se o
     *         produto alvo já tiver variações cadastradas
     * @throws com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException
     *         se {@code kitSku} já for componente de outro kit
     * @throws com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException se algum
     *         componente for o próprio {@code kitSku}
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException se
     *         o mesmo {@code componentSku} aparecer mais de uma vez na lista
     * @throws com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException se
     *         algum componente não for {@code SIMPLES}
     */
    Product defineKitRecipe(String kitSku, List<KitComponentCommand> components);

    /**
     * Receita vigente de um kit. Lista vazia se o SKU existe mas nunca foi promovido a kit.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir no catálogo
     */
    List<KitComponent> getKitRecipe(String kitSku);
}
