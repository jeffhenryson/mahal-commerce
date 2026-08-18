package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NfeImportRepositoryIT {

    @Autowired NfeImportRepositoryImpl nfeImportRepository;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static NfeImportLine matchedLine() {
        return NfeImportLine.fromXml(1, "FORN-001", "7891234567890", "Essência Menta", new BigDecimal("10.000"),
                new BigDecimal("12.50"), "L2026A", LocalDate.of(2027, 6, 1), "ESS-MENTA-50");
    }

    private static NfeImportLine unmatchedLine() {
        return NfeImportLine.fromXml(2, "FORN-002", null, "Carvão a granel", new BigDecimal("5.000"),
                new BigDecimal("8.00"), null, null, null);
    }

    @Test
    void save_persistsAndReloadsAPreviewedImportWithBothLineKinds() {
        NfeImport saved = nfeImportRepository.save(NfeImport.previewed(7L, "12345678000199", "uuid.xml",
                List.of(matchedLine(), unmatchedLine()), "comprador1"));
        flushAndClear();

        NfeImport reloaded = nfeImportRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(NfeImportStatus.PREVIEWED);
        assertThat(reloaded.supplierId()).isEqualTo(7L);
        assertThat(reloaded.emitterCnpj()).isEqualTo("12345678000199");
        assertThat(reloaded.fileReference()).isEqualTo("uuid.xml");
        assertThat(reloaded.lines()).hasSize(2);

        NfeImportLine matched = reloaded.lines().stream().filter(l -> l.itemNumber() == 1).findFirst().orElseThrow();
        assertThat(matched.ean()).isEqualTo("7891234567890");
        assertThat(matched.matchStatus()).isEqualTo(NfeImportLine.MatchStatus.MATCHED);
        assertThat(matched.matchedSku()).isEqualTo("ESS-MENTA-50");
        assertThat(matched.lotCode()).isEqualTo("L2026A");
        assertThat(matched.expiryDate()).isEqualTo(LocalDate.of(2027, 6, 1));

        NfeImportLine unmatched = reloaded.lines().stream().filter(l -> l.itemNumber() == 2).findFirst().orElseThrow();
        assertThat(unmatched.ean()).isNull();
        assertThat(unmatched.matchStatus()).isEqualTo(NfeImportLine.MatchStatus.UNMATCHED);
        assertThat(unmatched.matchedSku()).isNull();
    }

    @Test
    void save_persistsARejectedImportWithoutSupplier() {
        NfeImport saved = nfeImportRepository.save(NfeImport.rejected("00000000000000", "uuid.xml",
                List.of(matchedLine()), "comprador1"));
        flushAndClear();

        NfeImport reloaded = nfeImportRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(NfeImportStatus.REJECTED);
        assertThat(reloaded.supplierId()).isNull();
        assertThat(reloaded.goodsReceiptId()).isNull();
    }

    @Test
    void save_transitionsToConfirmedAndPersistsGoodsReceiptId() {
        NfeImport previewed = nfeImportRepository.save(NfeImport.previewed(7L, "12345678000199", "uuid.xml",
                List.of(matchedLine()), "comprador1"));
        flushAndClear();
        previewed = nfeImportRepository.findById(previewed.id()).orElseThrow();

        NfeImport confirmed = nfeImportRepository.save(
                previewed.confirmed("LOJA-01", 999L, Instant.now()));
        flushAndClear();

        NfeImport reloaded = nfeImportRepository.findById(confirmed.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(NfeImportStatus.CONFIRMED);
        assertThat(reloaded.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(reloaded.goodsReceiptId()).isEqualTo(999L);
        assertThat(reloaded.confirmedAt()).isNotNull();
    }

    @Test
    void save_persistsLinesUpdatedWithManualOverrides() {
        NfeImport previewed = nfeImportRepository.save(NfeImport.previewed(7L, "12345678000199", "uuid.xml",
                List.of(unmatchedLine()), "comprador1"));
        flushAndClear();
        previewed = nfeImportRepository.findById(previewed.id()).orElseThrow();

        NfeImportLine overridden = previewed.lines().get(0).withMatchedSku("CARV-MANUAL");
        nfeImportRepository.save(previewed.withLines(List.of(overridden)));
        flushAndClear();

        NfeImport reloaded = nfeImportRepository.findById(previewed.id()).orElseThrow();
        assertThat(reloaded.lines()).singleElement().satisfies(line -> {
            assertThat(line.matchStatus()).isEqualTo(NfeImportLine.MatchStatus.MATCHED);
            assertThat(line.matchedSku()).isEqualTo("CARV-MANUAL");
        });
    }
}
