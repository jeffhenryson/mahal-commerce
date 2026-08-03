package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.EstoqueUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationExpiryCleanupServiceTest {

    @Mock
    EstoqueUseCase estoqueUseCase;

    @Test
    void cleanup_delegatesToUseCase_withConfiguredBatchSize() {
        StockReservationExpiryCleanupService service = new StockReservationExpiryCleanupService(estoqueUseCase, 200);
        when(estoqueUseCase.expireReservations(200)).thenReturn(3);

        service.cleanup();

        verify(estoqueUseCase).expireReservations(eq(200));
    }
}
