package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

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
    Product createProduct(String sku, String name, String category, List<ProductVariant> variants, Pricing pricing);

    /** Lista produtos paginados. */
    PageResult<Product> listProducts(int page, int size);

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
    Product updateProduct(String sku, String name, String category, Pricing pricing);

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
     * Ativa ou desativa um produto (EST-F018). Produto inativo <b>recusa entrada</b> de estoque
     * — manual ou por recebimento de Compras —, mas continua aceitando saída, para escoar o saldo
     * remanescente. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * pai não existir.
     */
    Product setProductActive(String sku, boolean active);

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
     * Consulta o saldo de um SKU em um depósito. Retorna saldo zero se ainda não houve
     * nenhuma movimentação para o par SKU/depósito. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    StockBalance getStockBalance(String sku, String warehouseCode);

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
     * Histórico paginado de movimentações de um SKU em um depósito, mais recentes primeiro.
     * Retorna página vazia se o par SKU/depósito nunca foi movimentado. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
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
     * Registra o que foi contado de um SKU. É upsert por SKU — recontar sobrescreve. Exige
     * balanço {@link com.cernecommerce.core.domain.model.estoque.StockCountStatus#ABERTA} e SKU
     * existente no catálogo.
     */
    StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity);

    /**
     * Fecha o balanço e aplica os ajustes: para cada item cuja contagem <b>divirja</b> do saldo do
     * sistema, grava um {@link MovementType#AJUSTE} levando o saldo ao valor contado. Item que
     * bateu não gera movimentação — contagem certa não polui o ledger.
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
}
