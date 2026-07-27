package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa os adapters de persistência de estoque contra banco real (EST-C007).
 *
 * <p>Nenhum {@code *RepositoryImpl} de estoque era exercitado diretamente: o mapeamento
 * domínio↔entidade, o padrão ID-first da listagem de produtos e a propagação do {@code version}
 * só apareciam de lado, por ITs de controller que passariam mesmo com o mapeamento errado em
 * detalhes.</p>
 *
 * <p><b>Por que não é um {@code @DataJpaTest}:</b> no Spring Boot 4 as slices de teste saíram do
 * {@code spring-boot-test-autoconfigure} (que ficou com 22 classes) para módulos por tecnologia,
 * e o artefato que traz {@code @DataJpaTest}/{@code @AutoConfigureTestDatabase} não está no
 * classpath deste projeto. Daí o {@code @SpringBootTest} + {@code @Transactional}, que é o padrão
 * já usado pelos demais ITs.</p>
 *
 * <p>Cada teste roda numa transação que sofre rollback ao final. Onde a asserção depende do que
 * foi realmente gravado, há {@code flush()} + {@code clear()} explícitos — sem isso a leitura
 * voltaria do cache de primeiro nível e não provaria nada sobre o mapeamento.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EstoqueRepositoryIT {

    @Autowired ProductRepositoryImpl productRepository;
    @Autowired WarehouseRepositoryImpl warehouseRepository;
    @Autowired StockBalanceRepositoryImpl stockBalanceRepository;
    @Autowired StockMovementRepositoryImpl stockMovementRepository;
    @Autowired ReorderPointRepositoryImpl reorderPointRepository;
    @Autowired StockIntegrityRepositoryImpl stockIntegrityRepository;

    @PersistenceContext EntityManager em;

    /** Força a ida ao banco: sem isso a releitura viria do cache de primeiro nível. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Warehouse givenWarehouse(String code) {
        return warehouseRepository.save(Warehouse.create(code, "Depósito " + code, WarehouseType.LOJA_FISICA));
    }

    @Test
    void product_roundTrip_preservaVariacoesEAtributos() {
        Product saved = productRepository.save(Product.create("RT-001", "Narguilé", "narguile",
                List.of(ProductVariant.create("RT-001-M", List.of(new ProductAttribute("sabor", "menta"))))));
        flushAndClear();

        Optional<Product> found = productRepository.findBySku("RT-001");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isNotNull().isEqualTo(saved.id());
        assertThat(found.get().name()).isEqualTo("Narguilé");
        assertThat(found.get().active()).isTrue();
        assertThat(found.get().variants()).singleElement()
                .satisfies(v -> {
                    assertThat(v.sku()).isEqualTo("RT-001-M");
                    assertThat(v.attributes()).containsExactly(new ProductAttribute("sabor", "menta"));
                });
    }

    @Test
    void existsBySku_encontraTantoSkuPaiQuantoSkuDeVariacao() {
        productRepository.save(Product.create("EX-001", "Essência", "essencia",
                List.of(ProductVariant.create("EX-001-UVA", List.of(new ProductAttribute("sabor", "uva"))))));
        flushAndClear();

        // É a checagem que sustenta o EST-C002: se ela não enxergar o SKU de variação, toda venda
        // de variação passa a ser recusada com 404.
        assertThat(productRepository.existsBySku("EX-001")).as("SKU pai").isTrue();
        assertThat(productRepository.existsBySku("EX-001-UVA")).as("SKU de variação").isTrue();
        assertThat(productRepository.existsBySku("EX-999")).as("SKU inexistente").isFalse();
    }

    @Test
    void existsBySku_produtoSemVariacao_soCasaComOProprioSku() {
        productRepository.save(Product.create("EX-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        assertThat(productRepository.existsBySku("EX-002")).isTrue();
        assertThat(productRepository.existsBySku("EX-002-X")).isFalse();
    }

    @Test
    void findAll_paginaComIdFirst_semPerderVariacoes() {
        productRepository.save(Product.create("PG-001", "Produto 1", "cat",
                List.of(ProductVariant.create("PG-001-A", List.of(new ProductAttribute("cor", "azul"))))));
        productRepository.save(Product.create("PG-002", "Produto 2", "cat", List.of()));
        flushAndClear();

        PageResult<Product> page = productRepository.findAll(0, 1);

        assertThat(page.content()).hasSize(1);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.totalPages()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void warehouse_roundTripEFindByCode() {
        Warehouse saved = givenWarehouse("WH-RT");
        flushAndClear();

        assertThat(saved.id()).isNotNull();
        assertThat(warehouseRepository.findByCode("WH-RT")).isPresent()
                .get().satisfies(w -> assertThat(w.type()).isEqualTo(WarehouseType.LOJA_FISICA));
        assertThat(warehouseRepository.findByCode("WH-INEXISTENTE")).isEmpty();
        assertThat(warehouseRepository.findAll()).extracting(Warehouse::code).contains("WH-RT");
    }

    @Test
    void stockBalance_persisteSaldoEAvancaVersionAoAtualizar() {
        Warehouse warehouse = givenWarehouse("WH-VER");

        StockBalance inserted = stockBalanceRepository.save(
                StockBalance.zero("VER-001", warehouse.id()).apply(MovementType.ENTRADA, new BigDecimal("10.000")));
        flushAndClear();

        assertThat(inserted.id()).isNotNull();

        StockBalance reread = stockBalanceRepository.findBySkuAndWarehouseId("VER-001", warehouse.id()).orElseThrow();
        assertThat(reread.quantity()).isEqualByComparingTo("10.000");

        stockBalanceRepository.save(reread.apply(MovementType.SAIDA, new BigDecimal("4.000")));
        flushAndClear();

        StockBalance afterUpdate = stockBalanceRepository.findBySkuAndWarehouseId("VER-001", warehouse.id()).orElseThrow();
        assertThat(afterUpdate.quantity()).isEqualByComparingTo("6.000");
        assertThat(afterUpdate.version())
                .as("o @Version tem que avançar a cada escrita — é o que detecta o conflito concorrente")
                .isGreaterThan(reread.version());
    }

    @Test
    void stockMovement_paginaDoMaisRecenteParaOMaisAntigo() {
        // Grava três movimentos em sequência imediata, como faz uma venda com três itens: o
        // created_at sai idêntico e a ordenação tem que se apoiar no desempate por id, senão a
        // ordem vira arbitrária e a paginação fica instável.
        Warehouse warehouse = givenWarehouse("WH-MOV");
        stockMovementRepository.save(StockMovement.create("MOV-001", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("10.000"), "Primeira", "gerente"));
        stockMovementRepository.save(StockMovement.create("MOV-001", warehouse.id(), MovementType.SAIDA,
                new BigDecimal("2.000"), "Segunda", "gerente"));
        stockMovementRepository.save(StockMovement.create("MOV-001", warehouse.id(), MovementType.SAIDA,
                new BigDecimal("1.000"), "Terceira", "gerente"));
        flushAndClear();

        PageResult<StockMovement> page =
                stockMovementRepository.findBySkuAndWarehouseId("MOV-001", warehouse.id(), 0, 10);

        assertThat(page.content()).hasSize(3);
        assertThat(page.content()).extracting(StockMovement::reason)
                .as("mais recente primeiro, com desempate determinístico por id")
                .containsExactly("Terceira", "Segunda", "Primeira");
        assertThat(page.content().get(0).username()).isEqualTo("gerente");
        assertThat(page.content().get(0).createdAt()).isNotNull();
    }

    @Test
    void stockMovement_paginacaoNaoRepeteNemPulaLinhaComCreatedAtIgual() {
        // O risco real do EST-C012: com chave de ordenação não-única, a mesma linha pode voltar em
        // duas páginas ou não aparecer em nenhuma.
        Warehouse warehouse = givenWarehouse("WH-PAG");
        for (int i = 1; i <= 5; i++) {
            stockMovementRepository.save(StockMovement.create("PAG-001", warehouse.id(), MovementType.ENTRADA,
                    new BigDecimal("1.000"), "Movimento " + i, "gerente"));
        }
        flushAndClear();

        List<String> primeiraPagina = stockMovementRepository
                .findBySkuAndWarehouseId("PAG-001", warehouse.id(), 0, 2)
                .content().stream().map(StockMovement::reason).toList();
        List<String> segundaPagina = stockMovementRepository
                .findBySkuAndWarehouseId("PAG-001", warehouse.id(), 1, 2)
                .content().stream().map(StockMovement::reason).toList();
        List<String> terceiraPagina = stockMovementRepository
                .findBySkuAndWarehouseId("PAG-001", warehouse.id(), 2, 2)
                .content().stream().map(StockMovement::reason).toList();

        assertThat(primeiraPagina).hasSize(2);
        assertThat(segundaPagina).hasSize(2);
        assertThat(terceiraPagina).hasSize(1);
        assertThat(primeiraPagina).doesNotContainAnyElementsOf(segundaPagina);
        assertThat(terceiraPagina).doesNotContainAnyElementsOf(primeiraPagina)
                .doesNotContainAnyElementsOf(segundaPagina);
        assertThat(Stream.of(primeiraPagina, segundaPagina, terceiraPagina).flatMap(List::stream).toList())
                .as("as três páginas juntas têm que cobrir os 5 movimentos, sem repetir nem pular")
                .containsExactly("Movimento 5", "Movimento 4", "Movimento 3", "Movimento 2", "Movimento 1");
    }

    @Test
    void stockMovement_paginaVaziaQuandoParNuncaMovimentado() {
        Warehouse warehouse = givenWarehouse("WH-EMPTY");
        flushAndClear();

        PageResult<StockMovement> page =
                stockMovementRepository.findBySkuAndWarehouseId("SEM-USO", warehouse.id(), 0, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void reorderPoint_upsertReaproveitaOId() {
        Warehouse warehouse = givenWarehouse("WH-RP");
        ReorderPoint created = reorderPointRepository.save(
                new ReorderPoint(null, "RP-001", warehouse.id(), new BigDecimal("5.000")));
        flushAndClear();

        assertThat(created.id()).isNotNull();

        reorderPointRepository.save(new ReorderPoint(created.id(), "RP-001", warehouse.id(), new BigDecimal("12.000")));
        flushAndClear();

        assertThat(reorderPointRepository.findBySkuAndWarehouseId("RP-001", warehouse.id()))
                .get().satisfies(rp -> {
                    assertThat(rp.id()).as("upsert não pode criar uma segunda linha para o mesmo par")
                            .isEqualTo(created.id());
                    assertThat(rp.minQuantity()).isEqualByComparingTo("12.000");
                });
    }

    // ------------------------------------------------------------------------------------------
    // EST-C011 — diagnóstico de SKU órfão.
    //
    // É o único teste que exercita a query nativa de StockIntegrityJpaRepository; service e
    // controller só delegam. As asserções filtram pelos SKUs criados aqui em vez de comparar a
    // página inteira, porque a consulta varre a base toda e não pode assumir que ela está vazia.
    // ------------------------------------------------------------------------------------------

    /** Varre todas as páginas e devolve só as linhas cujo SKU começa com o prefixo do teste. */
    private List<OrphanSku> orphansWithPrefix(String prefix) {
        PageResult<OrphanSku> first = stockIntegrityRepository.findOrphanSkus(0, 100);
        List<OrphanSku> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) {
            all.addAll(stockIntegrityRepository.findOrphanSkus(page, 100).content());
        }
        return all.stream().filter(o -> o.sku().startsWith(prefix)).toList();
    }

    @Test
    void orphanSkus_naoAcusaSkuPaiNemSkuDeVariacaoCadastrados() {
        Warehouse warehouse = givenWarehouse("WH-ORF-OK");
        productRepository.save(Product.create("ORF-OK-001", "Essência", "essencia",
                List.of(ProductVariant.create("ORF-OK-001-UVA", List.of(new ProductAttribute("sabor", "uva"))))));
        // Movimenta os dois: o SKU pai e o de variação. Se o anti-join olhasse só product,
        // toda variação com saldo apareceria como órfã — o mesmo furo que o EST-C002 fechou.
        stockBalanceRepository.save(StockBalance.of(null, "ORF-OK-001", warehouse.id(), new BigDecimal("5.000"), 0L));
        stockBalanceRepository.save(
                StockBalance.of(null, "ORF-OK-001-UVA", warehouse.id(), new BigDecimal("3.000"), 0L));
        stockMovementRepository.save(StockMovement.create("ORF-OK-001-UVA", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("3.000"), "Recebimento", "gerente"));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-OK-")).isEmpty();
    }

    @Test
    void orphanSkus_acusaSkuForaDoCatalogoComSaldo() {
        Warehouse warehouse = givenWarehouse("WH-ORF-BAL");
        stockBalanceRepository.save(
                StockBalance.of(null, "ORF-BAL-999", warehouse.id(), new BigDecimal("7.500"), 0L));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-BAL-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.sku()).isEqualTo("ORF-BAL-999");
            assertThat(orphan.warehouseCode()).isEqualTo("WH-ORF-BAL");
            assertThat(orphan.quantity()).isEqualByComparingTo("7.500");
            assertThat(orphan.movementCount()).isZero();
            assertThat(orphan.hasReorderPoint()).isFalse();
            assertThat(orphan.lastMovementAt()).as("nunca movimentado").isNull();
        });
    }

    /**
     * Caso mais provável na prática: o ledger é imutável, então sobra movimento sem linha de saldo.
     *
     * <p>O {@code lastMovementAt} é conferido contra o que o próprio
     * {@code StockMovementRepository} devolve para o movimento mais recente, e não contra um
     * {@code Instant} literal. A query de diagnóstico é nativa e recebe o tipo cru do driver para
     * {@code created_at}, enquanto o ledger passa pelo mapeamento de entidade do Hibernate:
     * comparar os dois é justamente o que prova que a conversão do adapter não desloca o
     * instante.</p>
     */
    @Test
    void orphanSkus_acusaSkuSoComMovimentacoesEDevolveSaldoZero() {
        Warehouse warehouse = givenWarehouse("WH-ORF-MOV");
        stockMovementRepository.save(StockMovement.of(null, "ORF-MOV-999", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("2.000"), "Entrada antiga", "gerente", Instant.parse("2026-01-10T08:00:00Z")));
        stockMovementRepository.save(StockMovement.of(null, "ORF-MOV-999", warehouse.id(), MovementType.SAIDA,
                new BigDecimal("2.000"), "Saída recente", "gerente", Instant.parse("2026-03-20T15:30:00Z")));
        flushAndClear();

        // O ledger já ordena do mais recente para o mais antigo (EST-C012).
        Instant maisRecenteNoLedger = stockMovementRepository
                .findBySkuAndWarehouseId("ORF-MOV-999", warehouse.id(), 0, 1)
                .content().getFirst().createdAt();

        assertThat(orphansWithPrefix("ORF-MOV-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.quantity()).as("sem linha de saldo, o COALESCE devolve zero")
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(orphan.movementCount()).isEqualTo(2L);
            assertThat(orphan.lastMovementAt()).as("MAX(created_at), não o primeiro movimento")
                    .isEqualTo(maisRecenteNoLedger);
        });
    }

    @Test
    void orphanSkus_acusaSkuSoComPontoDeReposicao() {
        Warehouse warehouse = givenWarehouse("WH-ORF-RP");
        reorderPointRepository.save(new ReorderPoint(null, "ORF-RP-999", warehouse.id(), new BigDecimal("10.000")));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-RP-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.hasReorderPoint()).isTrue();
            assertThat(orphan.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(orphan.movementCount()).isZero();
            assertThat(orphan.lastMovementAt()).isNull();
        });
    }

    @Test
    void orphanSkus_umaLinhaPorDeposito() {
        Warehouse loja = givenWarehouse("WH-ORF-A");
        Warehouse ecommerce = givenWarehouse("WH-ORF-B");
        stockBalanceRepository.save(StockBalance.of(null, "ORF-DUP-999", loja.id(), new BigDecimal("4.000"), 0L));
        stockBalanceRepository.save(StockBalance.of(null, "ORF-DUP-999", ecommerce.id(), new BigDecimal("6.000"), 0L));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-DUP-"))
                .as("o UNION não pode colapsar os depósitos: cada um é uma decisão separada")
                .hasSize(2)
                .extracting(OrphanSku::warehouseCode)
                .containsExactly("WH-ORF-A", "WH-ORF-B");
    }

    /**
     * O mesmo par SKU/depósito nas três tabelas tem que virar uma linha só, não três — é para
     * isso que a origem é {@code UNION} e não {@code UNION ALL}.
     */
    @Test
    void orphanSkus_naoDuplicaQuandoOParEstaNasTresTabelas() {
        Warehouse warehouse = givenWarehouse("WH-ORF-ALL");
        stockBalanceRepository.save(StockBalance.of(null, "ORF-ALL-999", warehouse.id(), new BigDecimal("9.000"), 0L));
        stockMovementRepository.save(StockMovement.create("ORF-ALL-999", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("9.000"), "Entrada", "gerente"));
        reorderPointRepository.save(new ReorderPoint(null, "ORF-ALL-999", warehouse.id(), new BigDecimal("2.000")));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-ALL-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.quantity()).isEqualByComparingTo("9.000");
            assertThat(orphan.movementCount()).isEqualTo(1L);
            assertThat(orphan.hasReorderPoint()).isTrue();
            assertThat(orphan.lastMovementAt()).isNotNull();
        });
    }

    /**
     * Paginação estável: varrendo página a página com {@code size = 1}, cada órfão aparece
     * exatamente uma vez. A ordenação é por {@code (sku, warehouse_code)} justamente porque chave
     * não-única deixa o banco livre para repetir ou pular linha entre páginas (EST-C012).
     */
    @Test
    void orphanSkus_paginacaoNaoRepeteNemPulaLinha() {
        Warehouse warehouse = givenWarehouse("WH-ORF-PAG");
        Stream.of("ORF-PAG-001", "ORF-PAG-002", "ORF-PAG-003").forEach(sku ->
                stockBalanceRepository.save(StockBalance.of(null, sku, warehouse.id(), new BigDecimal("1.000"), 0L)));
        flushAndClear();

        PageResult<OrphanSku> primeira = stockIntegrityRepository.findOrphanSkus(0, 1);
        assertThat(primeira.size()).isEqualTo(1);
        assertThat(primeira.totalElements()).isGreaterThanOrEqualTo(3);

        List<String> varridos = new ArrayList<>();
        for (int page = 0; page < primeira.totalPages(); page++) {
            PageResult<OrphanSku> atual = stockIntegrityRepository.findOrphanSkus(page, 1);
            assertThat(atual.content()).as("página %d", page).hasSize(1);
            varridos.add(atual.content().getFirst().sku());
        }

        assertThat(varridos.stream().filter(sku -> sku.startsWith("ORF-PAG-")).toList())
                .as("cada órfão exatamente uma vez, em ordem de SKU")
                .containsExactly("ORF-PAG-001", "ORF-PAG-002", "ORF-PAG-003");
    }
}
