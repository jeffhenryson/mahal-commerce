package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionAssetTypeNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.SessionAssetUnavailableException;
import com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException;
import com.cernecommerce.core.domain.exception.pdv.SessionTierNotFoundException;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.ports.out.pdv.SessionMenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionMenuServiceTest {

    @Mock SessionMenuRepository repository;

    /** Quarta-feira, 23/09/2026 22:00 em São Paulo = quinta 01:00 UTC. */
    private static final Instant QUARTA_NOITE_SP = Instant.parse("2026-09-24T01:00:00Z");

    SessionMenuService service;

    private static final SessionAssetType VASO_P = new SessionAssetType(1L, "VASO_P", "Vaso pequeno", 2, false, true);
    private static final SessionAssetType VASO_G = new SessionAssetType(2L, "VASO_G", "Vaso grande", 1, false, true);
    private static final SessionAssetType PINCA = new SessionAssetType(3L, "PINCA", "Pinça", 3, true, true);
    private static final SessionAssetType TAPETE_INATIVO = new SessionAssetType(4L, "TAPETE", "Tapete", 0, true, false);
    private static final SessionSettings SETTINGS = new SessionSettings("VASO_P", "VASO_G", new BigDecimal("10.00"),
            Set.of(DayOfWeek.WEDNESDAY));

    @BeforeEach
    void setUp() {
        service = new SessionMenuService(repository, Clock.fixed(QUARTA_NOITE_SP, ZoneOffset.UTC));
    }

    // ── Utensílios ───────────────────────────────────────────────────────────────────────────

    @Test
    void reserveAssets_locksTheStandardVaseAndEveryActiveIncludedUtensil() {
        when(repository.findAssetTypeByCodigo("VASO_P")).thenReturn(Optional.of(VASO_P));
        when(repository.findAllAssetTypes()).thenReturn(List.of(VASO_P, VASO_G, PINCA, TAPETE_INATIVO));
        when(repository.lockAssetTypes(List.of(1L, 3L))).thenReturn(List.of(VASO_P, PINCA));
        when(repository.countInUseByAssetType()).thenReturn(Map.of(1L, 1));

        List<SessionAssetType> reserved = service.reserveAssetsForSession(SETTINGS, false);

        // Vaso grande não entra (não foi pedido) e tapete inativo não é exigido.
        assertThat(reserved).extracting(SessionAssetType::codigo).containsExactly("VASO_P", "PINCA");
    }

    @Test
    void reserveAssets_bigVase_usesTheBigVaseInsteadOfTheStandardOne() {
        when(repository.findAssetTypeByCodigo("VASO_G")).thenReturn(Optional.of(VASO_G));
        when(repository.findAllAssetTypes()).thenReturn(List.of(VASO_P, VASO_G, PINCA));
        when(repository.lockAssetTypes(List.of(2L, 3L))).thenReturn(List.of(VASO_G, PINCA));
        when(repository.countInUseByAssetType()).thenReturn(Map.of());

        assertThat(service.reserveAssetsForSession(SETTINGS, true))
                .extracting(SessionAssetType::codigo).containsExactly("VASO_G", "PINCA");
    }

    @Test
    void reserveAssets_whenAllInUse_refusesWithTheUtensilName() {
        when(repository.findAssetTypeByCodigo("VASO_G")).thenReturn(Optional.of(VASO_G));
        when(repository.findAllAssetTypes()).thenReturn(List.of(VASO_G, PINCA));
        when(repository.lockAssetTypes(List.of(2L, 3L))).thenReturn(List.of(VASO_G, PINCA));
        when(repository.countInUseByAssetType()).thenReturn(Map.of(2L, 1));

        assertThatThrownBy(() -> service.reserveAssetsForSession(SETTINGS, true))
                .isInstanceOf(SessionAssetUnavailableException.class)
                .hasMessageContaining("Vaso grande");
    }

    @Test
    void reserveAssets_withoutConfiguredVase_isAConflict() {
        SessionSettings semVaso = new SessionSettings(null, null, BigDecimal.ZERO, Set.of());

        assertThatThrownBy(() -> service.reserveAssetsForSession(semVaso, false))
                .isInstanceOf(SessionMenuConflictException.class);
        verify(repository, never()).lockAssetTypes(any());
    }

    @Test
    void allocateAndRelease_useTheClock() {
        service.allocate(100L, List.of(VASO_P, PINCA));
        service.release(List.of(100L));

        verify(repository).saveAllocations(argThat(list -> list.size() == 2
                && list.stream().allMatch(a -> a.comandaItemId().equals(100L) && a.alocadoEm().equals(QUARTA_NOITE_SP))));
        verify(repository).releaseAllocations(List.of(100L), QUARTA_NOITE_SP);
    }

    // ── Duplo rosh ───────────────────────────────────────────────────────────────────────────

    /** Quinta 01:00 UTC ainda é quarta à noite no salão — a promoção de quarta vale. */
    @Test
    void duploRoshDay_isTheSalonDay_notTheServerDay() {
        assertThat(service.isDuploRoshDay(SETTINGS, QUARTA_NOITE_SP)).isTrue();
        assertThat(service.isDuploRoshDay(SETTINGS, Instant.parse("2026-09-24T15:00:00Z"))).isFalse();
    }

    @Test
    void getMenu_showsOnlyActiveTiers_availabilityAndTodaysPromo() {
        SessionTier ativa = new SessionTier(1L, "Tradicional", new BigDecimal("25.00"), "Zgy, Zomo, Pred", 1, true);
        SessionTier inativa = new SessionTier(9L, "Antiga", new BigDecimal("20.00"), null, 9, false);
        when(repository.getSettings()).thenReturn(SETTINGS);
        when(repository.findAllTiers()).thenReturn(List.of(ativa, inativa));
        when(repository.findAllAssetTypes()).thenReturn(List.of(VASO_P, TAPETE_INATIVO));
        when(repository.countInUseByAssetType()).thenReturn(Map.of(1L, 2));

        SessionMenu menu = service.getMenu();

        assertThat(menu.faixas()).containsExactly(ativa);
        assertThat(menu.utensilios()).hasSize(1);
        assertThat(menu.utensilios().get(0).disponivel()).isZero();
        assertThat(menu.duploRoshHoje()).isTrue();
    }

    // ── Cadastro ─────────────────────────────────────────────────────────────────────────────

    @Test
    void createTier_withRepeatedName_isAConflict() {
        when(repository.findTierByNome("Premium"))
                .thenReturn(Optional.of(new SessionTier(2L, "Premium", BigDecimal.TEN, null, 0, true)));

        assertThatThrownBy(() -> service.createTier(" Premium ", new BigDecimal("30.00"), null, 2))
                .isInstanceOf(SessionMenuConflictException.class);
        verify(repository, never()).saveTier(any());
    }

    @Test
    void updateTier_keepsItsOwnName() {
        SessionTier atual = new SessionTier(2L, "Premium", new BigDecimal("30.00"), null, 2, true);
        when(repository.findTierById(2L)).thenReturn(Optional.of(atual));
        when(repository.findTierByNome("Premium")).thenReturn(Optional.of(atual));
        when(repository.saveTier(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionTier salvo = service.updateTier(2L, "Premium", new BigDecimal("32.00"), "Luk", 2, true);

        assertThat(salvo.preco()).isEqualByComparingTo("32.00");
    }

    @Test
    void requireActiveTier_refusesInactiveTier() {
        when(repository.findTierById(9L))
                .thenReturn(Optional.of(new SessionTier(9L, "Antiga", BigDecimal.TEN, null, 0, false)));

        assertThatThrownBy(() -> service.requireActiveTier(9L)).isInstanceOf(SessionTierNotFoundException.class);
    }

    @Test
    void createAssetType_withRepeatedCode_isAConflict() {
        when(repository.findAssetTypeByCodigo("PINCA")).thenReturn(Optional.of(PINCA));

        assertThatThrownBy(() -> service.createAssetType("pinca", "Pinça", 3, true))
                .isInstanceOf(SessionMenuConflictException.class);
    }

    @Test
    void updateSettings_withUnknownVaseCode_isNotFound() {
        when(repository.findAssetTypeByCodigo("VASO_X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSettings(
                new SessionSettings("VASO_X", null, BigDecimal.ZERO, Set.of())))
                .isInstanceOf(SessionAssetTypeNotFoundException.class);
        verify(repository, never()).saveSettings(any());
    }
}
