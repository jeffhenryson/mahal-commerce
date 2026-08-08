package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
    @Autowired StockCountRepositoryImpl stockCountRepository;
    @Autowired StockReservationRepositoryImpl stockReservationRepository;
    @Autowired StockLotRepositoryImpl stockLotRepository;
    @Autowired KitComponentRepositoryImpl kitComponentRepository;

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
    void product_roundTrip_preservaLotTracked() {
        productRepository.save(Product.create("RT-LOT-001", "Essência", "essencia", List.of())
                .withLotTracked(true));
        flushAndClear();

        assertThat(productRepository.findBySku("RT-LOT-001")).isPresent()
                .get().satisfies(p -> assertThat(p.lotTracked()).isTrue());
    }

    /**
     * Único teste que de fato exercita o mapeamento {@code @ElementCollection}+
     * {@code @OrderColumn} da galeria de imagens contra banco real — mocks não capturam erro
     * de mapeamento JPA.
     */
    @Test
    void product_roundTrip_preservaCamposDeMarketingEOrdemDaGaleria() {
        productRepository.save(Product.create("RT-MKT-001", "Narguilé", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), null, new BigDecimal("79.90"), new BigDecimal("99.90")),
                ProductType.SIMPLES, false, "Aladin", "http://img.png", true, true, "Descrição longa",
                "http://video.mp4", List.of("http://img1.png", "http://img2.png", "http://img3.png")));
        flushAndClear();

        Product found = productRepository.findBySku("RT-MKT-001").orElseThrow();

        assertThat(found.pricing().originalPrice()).isEqualByComparingTo("99.90");
        assertThat(found.superPromo()).isTrue();
        assertThat(found.description()).isEqualTo("Descrição longa");
        assertThat(found.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(found.images()).as("ordem da galeria preservada")
                .containsExactly("http://img1.png", "http://img2.png", "http://img3.png");
    }

    @Test
    void product_semCamposDeMarketing_voltaComGaleriaVaziaECamposNulos() {
        productRepository.save(Product.create("RT-MKT-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        Product found = productRepository.findBySku("RT-MKT-002").orElseThrow();

        assertThat(found.pricing().originalPrice()).isNull();
        assertThat(found.superPromo()).isFalse();
        assertThat(found.description()).isNull();
        assertThat(found.videoUrl()).isNull();
        assertThat(found.images()).isEmpty();
    }

    @Test
    void product_atualizarGaleria_substituiAListaInteiraSemDuplicar() {
        Product saved = productRepository.save(Product.create("RT-MKT-003", "Narguilé", "narguile", List.of())
                .withImages(List.of("http://old1.png", "http://old2.png")));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-MKT-003").orElseThrow();
        productRepository.save(reloaded.withImages(List.of("http://new1.png")));
        flushAndClear();

        Product found = productRepository.findBySku("RT-MKT-003").orElseThrow();
        assertThat(found.images()).containsExactly("http://new1.png");
    }

    // ---------- EST-F019 — precificação ----------

    @Test
    void product_roundTrip_preservaAPrecificacao() {
        productRepository.save(Product.create("PR-001", "Narguilé", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), new BigDecimal("80.5000"), new BigDecimal("79.90"))));
        flushAndClear();

        Pricing pricing = productRepository.findBySku("PR-001").orElseThrow().pricing();

        assertThat(pricing.costPrice()).isEqualByComparingTo("45.00");
        assertThat(pricing.markupPercent()).isEqualByComparingTo("80.5000");
        assertThat(pricing.salePrice()).isEqualByComparingTo("79.90");
    }

    /**
     * Produto sem preço tem que voltar como {@link Pricing#empty()}, e não com zeros: preço
     * desconhecido não é preço zero, e a diferença decide se o PDV recusa a venda ou dá o item
     * de graça.
     */
    @Test
    void product_semPreco_voltaComoPricingVazio() {
        productRepository.save(Product.create("PR-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        assertThat(productRepository.findBySku("PR-002").orElseThrow().pricing().isEmpty()).isTrue();
    }

    /** A escala 4 de markup_percent existe para sobreviver ao round-trip sem truncar. */
    @Test
    void markupPercent_preservaAsQuatroCasasDecimais() {
        productRepository.save(Product.create("PR-003", "Essência", "essencia", List.of(),
                Pricing.byMarkup(new BigDecimal("45.00"), new BigDecimal("33.3333"))));
        flushAndClear();

        Pricing pricing = productRepository.findBySku("PR-003").orElseThrow().pricing();

        assertThat(pricing.markupPercent()).isEqualByComparingTo("33.3333");
        assertThat(pricing.suggestedPrice()).isEqualByComparingTo("60.00");
    }

    @Test
    void findByAnySku_resolveOPaiTantoPeloSkuPaiQuantoPeloDeVariacao() {
        productRepository.save(Product.create("PR-004", "Essência", "essencia",
                List.of(ProductVariant.create("PR-004-UVA", List.of(new ProductAttribute("sabor", "uva")))),
                Pricing.of(null, null, new BigDecimal("29.90"))));
        flushAndClear();

        Product pelosPai = productRepository.findByAnySku("PR-004").orElseThrow();
        Product pelaVariacao = productRepository.findByAnySku("PR-004-UVA").orElseThrow();

        assertThat(pelosPai.sku()).isEqualTo("PR-004");
        assertThat(pelaVariacao.sku()).as("variação resolve para o produto pai").isEqualTo("PR-004");
        assertThat(pelaVariacao.pricing().effectivePrice()).as("e herda o preço dele")
                .isEqualByComparingTo("29.90");
        assertThat(productRepository.findByAnySku("PR-999")).as("SKU inexistente").isEmpty();
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

    /**
     * EST-F018: {@code isSkuActive} é distinto de {@code existsBySku} — o SKU continua existindo
     * depois de desativado, e a diferença entre os dois é o que separa 404 de 409.
     */
    @Test
    void isSkuActive_exigeProdutoPaiAtivo_inclusiveParaSkuDeVariacao() {
        Product saved = productRepository.save(Product.create("AT-001", "Essência", "essencia",
                List.of(ProductVariant.create("AT-001-UVA", List.of(new ProductAttribute("sabor", "uva"))))));
        flushAndClear();

        assertThat(productRepository.isSkuActive("AT-001")).as("pai ativo").isTrue();
        assertThat(productRepository.isSkuActive("AT-001-UVA")).as("variação de pai ativo").isTrue();

        productRepository.save(saved.withActive(false));
        flushAndClear();

        assertThat(productRepository.existsBySku("AT-001"))
                .as("desativar não apaga: o SKU continua existindo").isTrue();
        assertThat(productRepository.isSkuActive("AT-001")).as("pai desativado").isFalse();
        assertThat(productRepository.isSkuActive("AT-001-UVA"))
                .as("desativar o pai tira a grade inteira de circulação").isFalse();
    }

    @Test
    void isSkuActive_falseParaSkuInexistente() {
        assertThat(productRepository.isSkuActive("NAO-EXISTE-999")).isFalse();
    }

    /** O PATCH carrega o produto inteiro e regrava: as variações não podem sumir no caminho. */
    @Test
    void save_aposWithDetails_preservaVariacoesEAtributos() {
        Product saved = productRepository.save(Product.create("UP-001", "Nome Antigo", "cat-antiga",
                List.of(ProductVariant.create("UP-001-M", List.of(new ProductAttribute("sabor", "menta"))))));
        flushAndClear();

        Product carregado = productRepository.findBySku("UP-001").orElseThrow();
        productRepository.save(carregado.withDetails("Nome Novo", null));
        flushAndClear();

        assertThat(productRepository.findBySku("UP-001")).get().satisfies(p -> {
            assertThat(p.id()).isEqualTo(saved.id());
            assertThat(p.name()).isEqualTo("Nome Novo");
            assertThat(p.category()).as("null mantém").isEqualTo("cat-antiga");
            assertThat(p.variants()).singleElement().satisfies(v -> {
                assertThat(v.sku()).isEqualTo("UP-001-M");
                assertThat(v.attributes()).containsExactly(new ProductAttribute("sabor", "menta"));
            });
        });
    }

    @Test
    void warehouse_saveAposWithActive_persisteADesativacao() {
        Warehouse warehouse = givenWarehouse("WH-OFF");
        flushAndClear();

        warehouseRepository.save(warehouse.withActive(false));
        flushAndClear();

        assertThat(warehouseRepository.findByCode("WH-OFF")).get()
                .satisfies(w -> assertThat(w.active()).isFalse());
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
        assertThat(warehouseRepository.findAll(0, 100).content())
                .extracting(Warehouse::code).contains("WH-RT");
    }

    /**
     * EST-C005: a listagem de depósitos passou a ser paginada. A ordenação é por {@code id}
     * (BIGSERIAL monotônico, chave única) para a paginação ser estável — {@code code} também
     * seria único, mas {@code id} reflete a ordem de criação, que é a que o operador espera.
     */
    @Test
    void warehouse_paginaOrdenadoPorId() {
        Warehouse primeiro = givenWarehouse("WH-PAG-A");
        Warehouse segundo = givenWarehouse("WH-PAG-B");
        flushAndClear();

        PageResult<Warehouse> pagina = warehouseRepository.findAll(0, 1);

        assertThat(pagina.content()).hasSize(1);
        assertThat(pagina.size()).isEqualTo(1);
        assertThat(pagina.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(pagina.totalPages()).isGreaterThanOrEqualTo(2);

        // Varre todas as páginas: os dois depósitos aparecem uma vez cada, na ordem de criação.
        List<Long> ids = new ArrayList<>();
        for (int page = 0; page < pagina.totalPages(); page++) {
            warehouseRepository.findAll(page, 1).content().forEach(w -> ids.add(w.id()));
        }
        assertThat(ids).containsSequence(primeiro.id(), segundo.id());
        assertThat(ids).doesNotHaveDuplicates();
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
    void stockMovement_roundTrip_preservaLotCode() {
        Warehouse warehouse = givenWarehouse("WH-LOT-MOV");
        stockMovementRepository.save(StockMovement.create("LOT-MOV-001", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente", "LOTE-A"));
        flushAndClear();

        PageResult<StockMovement> page =
                stockMovementRepository.findBySkuAndWarehouseId("LOT-MOV-001", warehouse.id(), 0, 10);

        assertThat(page.content()).singleElement()
                .satisfies(m -> assertThat(m.lotCode()).isEqualTo("LOTE-A"));
    }

    // ---------- EST-F008 — lote e validade ----------

    @Test
    void stockLot_roundTrip_persisteEReconstitui() {
        Warehouse warehouse = givenWarehouse("WH-LOT");
        StockLot inserted = stockLotRepository.save(
                StockLot.create("LOT-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 3, 1))
                        .receive(new BigDecimal("10.000")));
        flushAndClear();

        assertThat(inserted.id()).isNotNull();

        StockLot reread = stockLotRepository
                .findBySkuAndWarehouseIdAndLotCode("LOT-001", warehouse.id(), "LOTE-A").orElseThrow();
        assertThat(reread.quantity()).isEqualByComparingTo("10.000");
        assertThat(reread.expiryDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(reread.alertedAt()).isNull();

        stockLotRepository.save(reread.consume(new BigDecimal("4.000")));
        flushAndClear();

        StockLot afterUpdate = stockLotRepository
                .findBySkuAndWarehouseIdAndLotCode("LOT-001", warehouse.id(), "LOTE-A").orElseThrow();
        assertThat(afterUpdate.quantity()).isEqualByComparingTo("6.000");
        assertThat(afterUpdate.version())
                .as("o @Version tem que avançar a cada escrita, mesmo padrão de stock_balance")
                .isGreaterThan(reread.version());
    }

    @Test
    void stockLot_findBySkuAndWarehouseId_ordenaPorFefo() {
        Warehouse warehouse = givenWarehouse("WH-FEFO");
        // Grava fora de ordem de vencimento de propósito — a consulta é quem tem que ordenar.
        stockLotRepository.save(StockLot.create("FEFO-001", warehouse.id(), "LOTE-C", LocalDate.of(2027, 6, 1))
                .receive(BigDecimal.ONE));
        stockLotRepository.save(StockLot.create("FEFO-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(BigDecimal.ONE));
        stockLotRepository.save(StockLot.create("FEFO-001", warehouse.id(), "LOTE-B", LocalDate.of(2027, 3, 1))
                .receive(BigDecimal.ONE));
        flushAndClear();

        List<StockLot> lots = stockLotRepository.findBySkuAndWarehouseId("FEFO-001", warehouse.id());

        assertThat(lots).extracting(StockLot::lotCode)
                .as("do que vence primeiro para o que vence por último")
                .containsExactly("LOTE-A", "LOTE-B", "LOTE-C");
    }

    @Test
    void stockLot_findExpiringSoon_respeitaCutoffQuantityEAlertedAt() {
        Warehouse warehouse = givenWarehouse("WH-EXP");
        stockLotRepository.save(StockLot.create("EXP-001", warehouse.id(), "LOTE-VENCIDO", LocalDate.of(2026, 1, 1))
                .receive(BigDecimal.TEN));
        stockLotRepository.save(StockLot.create("EXP-001", warehouse.id(), "LOTE-LONGE", LocalDate.of(2030, 1, 1))
                .receive(BigDecimal.TEN));
        StockLot semSaldo = stockLotRepository.save(
                StockLot.create("EXP-001", warehouse.id(), "LOTE-ZERADO", LocalDate.of(2026, 1, 1))
                        .receive(BigDecimal.TEN));
        stockLotRepository.save(semSaldo.consume(BigDecimal.TEN));
        StockLot jaAlertado = stockLotRepository.save(
                StockLot.create("EXP-001", warehouse.id(), "LOTE-ALERTADO", LocalDate.of(2026, 1, 1))
                        .receive(BigDecimal.TEN));
        stockLotRepository.save(jaAlertado.alerted());
        flushAndClear();

        List<StockLot> expiring = stockLotRepository.findExpiringSoon(LocalDate.of(2026, 12, 31), 10);

        assertThat(expiring).extracting(StockLot::lotCode)
                .as("vencido entra, sem saldo e já alertado ficam de fora, e o que vence longe não entra")
                .containsExactly("LOTE-VENCIDO");
    }

    /**
     * Caso de borda do cutoff: no teste acima, todo lote está bem antes ou bem depois da data de
     * corte — nenhum bate em cheio. Aqui o {@code expiryDate} é EXATAMENTE igual ao cutoff, o que
     * prova se a query usa {@code <=} (inclusivo) ou {@code <} (exclusivo).
     */
    @Test
    void stockLot_findExpiringSoon_incluiLoteComExpiryDateIgualAoCutoff() {
        Warehouse warehouse = givenWarehouse("WH-EXP-EDGE");
        LocalDate cutoff = LocalDate.of(2026, 12, 31);
        stockLotRepository.save(StockLot.create("EXP-EDGE-001", warehouse.id(), "LOTE-NO-CUTOFF", cutoff)
                .receive(BigDecimal.TEN));
        flushAndClear();

        List<StockLot> expiring = stockLotRepository.findExpiringSoon(cutoff, 10);

        assertThat(expiring).extracting(StockLot::lotCode)
                .as("expiryDate igual ao cutoff entra no resultado: a comparação é <=, não <")
                .containsExactly("LOTE-NO-CUTOFF");
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
    // EST-F006 — persistência do balanço de inventário.
    // ------------------------------------------------------------------------------------------

    @Test
    void stockCount_roundTripPreservaItensEDivergencias() {
        Warehouse warehouse = givenWarehouse("WH-CNT-RT");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-001", new BigDecimal("8.000"))
                .withCountedItem("CNT-002", new BigDecimal("0.000")));
        flushAndClear();

        assertThat(aberta.id()).isNotNull();
        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(StockCountStatus.ABERTA);
            assertThat(c.username()).isEqualTo("gerente");
            assertThat(c.closedAt()).isNull();
            assertThat(c.items()).hasSize(2);
            assertThat(c.items()).allSatisfy(item -> {
                assertThat(item.id()).isNotNull();
                assertThat(item.expectedQuantity()).as("aberta ainda não confrontou").isNull();
            });
        });
    }

    @Test
    void stockCount_fechamentoPersisteExpectedEDifference() {
        Warehouse warehouse = givenWarehouse("WH-CNT-CLOSE");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-010", new BigDecimal("8.000")));
        flushAndClear();

        StockCount carregada = stockCountRepository.findById(aberta.id()).orElseThrow();
        List<StockCountItem> reconciliados = carregada.items().stream()
                .map(i -> i.reconciledWith(new BigDecimal("10.000")))
                .toList();
        stockCountRepository.save(carregada.withReconciledItems(reconciliados).closed());
        flushAndClear();

        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(StockCountStatus.FECHADA);
            assertThat(c.closedAt()).isNotNull();
            assertThat(c.items()).singleElement().satisfies(item -> {
                assertThat(item.expectedQuantity()).isEqualByComparingTo("10.000");
                assertThat(item.difference()).isEqualByComparingTo("-2.000");
            });
        });
    }

    /** A regra de um balanço aberto por depósito depende desta consulta. */
    @Test
    void stockCount_findOpenByWarehouseId_soEnxergaAAberta() {
        Warehouse warehouse = givenWarehouse("WH-CNT-OPEN");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente"));
        flushAndClear();

        assertThat(stockCountRepository.findOpenByWarehouseId(warehouse.id()))
                .get().satisfies(c -> assertThat(c.id()).isEqualTo(aberta.id()));

        stockCountRepository.save(stockCountRepository.findById(aberta.id()).orElseThrow().closed());
        flushAndClear();

        assertThat(stockCountRepository.findOpenByWarehouseId(warehouse.id()))
                .as("fechada não conta como aberta").isEmpty();
    }

    @Test
    void stockCount_recontagemNaoDuplicaLinhaDoMesmoSku() {
        Warehouse warehouse = givenWarehouse("WH-CNT-DUP");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-020", new BigDecimal("3.000")));
        flushAndClear();

        StockCount carregada = stockCountRepository.findById(aberta.id()).orElseThrow();
        stockCountRepository.save(carregada.withCountedItem("CNT-020", new BigDecimal("9.000")));
        flushAndClear();

        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c ->
                assertThat(c.items()).as("uk_stock_count_item_count_sku").singleElement()
                        .satisfies(item -> assertThat(item.countedQuantity()).isEqualByComparingTo("9.000")));
    }

    /**
     * EST-F008: lote diferente do mesmo SKU é linha nova; recontar o mesmo lote sobrescreve —
     * espelha {@code stockCount_recontagemNaoDuplicaLinhaDoMesmoSku}, mas por
     * {@code (sku, lotCode)} em vez de só {@code sku}.
     */
    @Test
    void stockCount_recontagemPorLote_naoDuplicaLinhaDoMesmoLoteEPreservaLotesDiferentes() {
        Warehouse warehouse = givenWarehouse("WH-CNT-LOT");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-LOT-001", new BigDecimal("3.000"), "LOTE-A")
                .withCountedItem("CNT-LOT-001", new BigDecimal("5.000"), "LOTE-B"));
        flushAndClear();

        StockCount carregada = stockCountRepository.findById(aberta.id()).orElseThrow();
        stockCountRepository.save(carregada.withCountedItem("CNT-LOT-001", new BigDecimal("9.000"), "LOTE-A"));
        flushAndClear();

        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c ->
                assertThat(c.items()).extracting(StockCountItem::lotCode, StockCountItem::countedQuantity)
                        .as("LOTE-A recontado sobrescreve, LOTE-B continua intacto — nunca 3 linhas")
                        .containsExactlyInAnyOrder(
                                tuple("LOTE-A", new BigDecimal("9.000")),
                                tuple("LOTE-B", new BigDecimal("5.000"))));
    }

    @Test
    void stockCount_listaDoMaisRecenteParaOMaisAntigo() {
        Warehouse warehouse = givenWarehouse("WH-CNT-PAG");
        for (int i = 0; i < 3; i++) {
            StockCount c = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente"));
            stockCountRepository.save(stockCountRepository.findById(c.id()).orElseThrow().closed());
        }
        flushAndClear();

        PageResult<StockCount> page = stockCountRepository.findByWarehouseId(warehouse.id(), 0, 2);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).id())
                .as("mais recente primeiro, com desempate por id").isGreaterThan(page.content().get(1).id());
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

    // ------------------------------------------------------------------------------------------
    // EST-C013 — diagnóstico de divergência entre o contador de reservado e o ledger de reservas.
    //
    // Mesmo espírito do EST-C011 acima: é o único teste que exercita a query nativa de
    // findReservationMismatches; service e controller só delegam.
    // ------------------------------------------------------------------------------------------

    /** Varre todas as páginas e devolve só as linhas cujo SKU começa com o prefixo do teste. */
    private List<ReservationIntegrityMismatch> reservationMismatchesWithPrefix(String prefix) {
        PageResult<ReservationIntegrityMismatch> first = stockIntegrityRepository.findReservationMismatches(0, 100);
        List<ReservationIntegrityMismatch> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) {
            all.addAll(stockIntegrityRepository.findReservationMismatches(page, 100).content());
        }
        return all.stream().filter(m -> m.sku().startsWith(prefix)).toList();
    }

    @Test
    void reservationMismatch_detectaContadorAcimaDoLedger() {
        Warehouse warehouse = givenWarehouse("WH-RSV-CTR");
        stockBalanceRepository.save(StockBalance.of(null, "RSV-CTR-001", warehouse.id(),
                new BigDecimal("10.000"), new BigDecimal("5.000"), 0L));
        stockReservationRepository.save(StockReservation.create("RSV-CTR-001", warehouse.id(),
                new BigDecimal("3.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600), "gerente"));
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-CTR-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.reservedQuantity()).isEqualByComparingTo("5.000");
            assertThat(mismatch.activeReservationsTotal()).isEqualByComparingTo("3.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("2.000");
        });
    }

    @Test
    void reservationMismatch_detectaLedgerAcimaDoContador() {
        Warehouse warehouse = givenWarehouse("WH-RSV-LDG");
        // Nenhum stock_balance salvo de propósito: a divergência pode vir só do lado do ledger.
        stockReservationRepository.save(StockReservation.create("RSV-LDG-001", warehouse.id(),
                new BigDecimal("2.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600), "gerente"));
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-LDG-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.reservedQuantity()).isEqualByComparingTo("0");
            assertThat(mismatch.activeReservationsTotal()).isEqualByComparingTo("2.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("-2.000");
        });
    }

    @Test
    void reservationMismatch_naoAcusaQuandoContadorBateComLedger() {
        Warehouse warehouse = givenWarehouse("WH-RSV-OK");
        stockBalanceRepository.save(StockBalance.of(null, "RSV-OK-001", warehouse.id(),
                new BigDecimal("10.000"), new BigDecimal("4.000"), 0L));
        stockReservationRepository.save(StockReservation.create("RSV-OK-001", warehouse.id(),
                new BigDecimal("4.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600), "gerente"));
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-OK-")).isEmpty();
    }

    @Test
    void reservationMismatch_ignoraReservaJaResolvida() {
        Warehouse warehouse = givenWarehouse("WH-RSV-RES");
        stockBalanceRepository.save(StockBalance.of(null, "RSV-RES-001", warehouse.id(),
                new BigDecimal("10.000"), BigDecimal.ZERO, 0L));
        StockReservation resolvida = stockReservationRepository.save(StockReservation.create("RSV-RES-001",
                warehouse.id(), new BigDecimal("6.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600),
                "gerente"));
        stockReservationRepository.save(resolvida.released());
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-RES-"))
                .as("reserva RELEASED não conta no ledger ativo, e o contador já foi devolvido a zero")
                .isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // EST-F008 — diagnóstico de divergência entre o agregado (stock_balance) e a soma dos lotes
    // (stock_lot). Mesmo espírito do EST-C011/C013 acima: é o único teste que exercita a query
    // nativa de findLotMismatches; service e controller só delegam.
    // ------------------------------------------------------------------------------------------

    /** Varre todas as páginas e devolve só as linhas cujo SKU começa com o prefixo do teste. */
    private List<LotIntegrityMismatch> lotMismatchesWithPrefix(String prefix) {
        PageResult<LotIntegrityMismatch> first = stockIntegrityRepository.findLotMismatches(0, 100);
        List<LotIntegrityMismatch> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) {
            all.addAll(stockIntegrityRepository.findLotMismatches(page, 100).content());
        }
        return all.stream().filter(m -> m.sku().startsWith(prefix)).toList();
    }

    @Test
    void lotMismatch_detectaAgregadoAcimaDosLotes() {
        Warehouse warehouse = givenWarehouse("WH-LOT-AGG");
        productRepository.save(Product.create("LOT-AGG-001", "Essência", "essencia", List.of())
                .withLotTracked(true));
        stockBalanceRepository.save(StockBalance.of(null, "LOT-AGG-001", warehouse.id(),
                new BigDecimal("10.000"), 0L));
        stockLotRepository.save(StockLot.create("LOT-AGG-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(new BigDecimal("6.000")));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-AGG-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.balanceQuantity()).isEqualByComparingTo("10.000");
            assertThat(mismatch.lotsTotal()).isEqualByComparingTo("6.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("4.000");
        });
    }

    @Test
    void lotMismatch_detectaLotesAcimaDoAgregado() {
        Warehouse warehouse = givenWarehouse("WH-LOT-LTS");
        // Nenhum stock_balance salvo de propósito: a divergência pode vir só do lado dos lotes.
        stockLotRepository.save(StockLot.create("LOT-LTS-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(new BigDecimal("5.000")));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-LTS-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.balanceQuantity()).isEqualByComparingTo("0");
            assertThat(mismatch.lotsTotal()).isEqualByComparingTo("5.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("-5.000");
        });
    }

    @Test
    void lotMismatch_naoAcusaQuandoAgregadoBateComLotes() {
        Warehouse warehouse = givenWarehouse("WH-LOT-OK");
        productRepository.save(Product.create("LOT-OK-001", "Essência", "essencia", List.of())
                .withLotTracked(true));
        stockBalanceRepository.save(StockBalance.of(null, "LOT-OK-001", warehouse.id(),
                new BigDecimal("6.000"), 0L));
        stockLotRepository.save(StockLot.create("LOT-OK-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(new BigDecimal("6.000")));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-OK-")).isEmpty();
    }

    /**
     * O filtro por {@code product.lot_tracked} existe justamente para isto: sem ele, todo SKU não
     * lote-rastreado com saldo (a maioria da base) apareceria como falso positivo, porque nunca
     * tem lote nenhum em {@code stock_lot}.
     */
    @Test
    void lotMismatch_ignoraProdutoNaoLoteRastreadoComSaldo() {
        Warehouse warehouse = givenWarehouse("WH-LOT-NT");
        productRepository.save(Product.create("LOT-NT-001", "Narguilé", "narguile", List.of()));
        stockBalanceRepository.save(StockBalance.of(null, "LOT-NT-001", warehouse.id(),
                new BigDecimal("10.000"), 0L));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-NT-")).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // EST-F015 — receita de kit virtual. Nenhum teste aqui exercitava KitComponentRepositoryImpl
    // diretamente: o mapeamento domínio↔entidade e o delete-then-insert de replaceRecipe só
    // apareciam de lado, por ITs de controller.
    // ------------------------------------------------------------------------------------------

    @Test
    void kitComponent_findByKitSku_devolveComponentesMapeados() {
        kitComponentRepository.replaceRecipe("KIT-001", List.of(
                KitComponent.create("KIT-001", "KIT-001-COMP-A", new BigDecimal("2.000")),
                KitComponent.create("KIT-001", "KIT-001-COMP-B", new BigDecimal("3.500"))));
        flushAndClear();

        List<KitComponent> componentes = kitComponentRepository.findByKitSku("KIT-001");

        assertThat(componentes).hasSize(2).allSatisfy(c -> {
            assertThat(c.id()).isNotNull();
            assertThat(c.kitSku()).isEqualTo("KIT-001");
        });
        assertThat(componentes).extracting(KitComponent::componentSku)
                .containsExactlyInAnyOrder("KIT-001-COMP-A", "KIT-001-COMP-B");
        assertThat(componentes.stream()
                .filter(c -> c.componentSku().equals("KIT-001-COMP-A")).findFirst().orElseThrow().quantity())
                .isEqualByComparingTo("2.000");
        assertThat(componentes.stream()
                .filter(c -> c.componentSku().equals("KIT-001-COMP-B")).findFirst().orElseThrow().quantity())
                .isEqualByComparingTo("3.500");
    }

    /**
     * Prova que {@code replaceRecipe} é de fato delete-then-insert contra o banco (
     * {@code deleteByKitSku} + {@code flush()} + {@code saveAll}), e não deixa lixo dos
     * componentes antigos: a receita nova com só o componente C não pode trazer A nem B de volta.
     */
    @Test
    void kitComponent_replaceRecipe_substituiComponentesAntigosPorNovos() {
        kitComponentRepository.replaceRecipe("KIT-002", List.of(
                KitComponent.create("KIT-002", "KIT-002-COMP-A", BigDecimal.ONE),
                KitComponent.create("KIT-002", "KIT-002-COMP-B", BigDecimal.ONE)));
        flushAndClear();

        assertThat(kitComponentRepository.findByKitSku("KIT-002")).extracting(KitComponent::componentSku)
                .as("receita inicial gravada com A e B")
                .containsExactlyInAnyOrder("KIT-002-COMP-A", "KIT-002-COMP-B");

        kitComponentRepository.replaceRecipe("KIT-002",
                List.of(KitComponent.create("KIT-002", "KIT-002-COMP-C", BigDecimal.ONE)));
        flushAndClear();

        assertThat(kitComponentRepository.findByKitSku("KIT-002")).extracting(KitComponent::componentSku)
                .as("A e B somem, só C fica: sem isso a receita antiga vazaria pro consumo de estoque")
                .containsExactly("KIT-002-COMP-C");
    }

    @Test
    void kitComponent_isUsedAsComponent_trueSoParaSkuQueEComponenteDeAlgumKit() {
        kitComponentRepository.replaceRecipe("KIT-003",
                List.of(KitComponent.create("KIT-003", "KIT-003-COMP-USADO", BigDecimal.ONE)));
        flushAndClear();

        assertThat(kitComponentRepository.isUsedAsComponent("KIT-003-COMP-USADO"))
                .as("SKU é componente de KIT-003: não pode ser promovido a kit também").isTrue();
        assertThat(kitComponentRepository.isUsedAsComponent("KIT-003-COMP-NAO-USADO"))
                .as("SKU não aparece em nenhuma receita").isFalse();
    }
}
