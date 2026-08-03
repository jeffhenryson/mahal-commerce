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
class StockLotExpiryAlertServiceTest {

    @Mock
    EstoqueUseCase estoqueUseCase;

    @Test
    void alert_delegatesToUseCase_withConfiguredCutoffAndBatchSize() {
        StockLotExpiryAlertService service = new StockLotExpiryAlertService(estoqueUseCase, 7, 200);
        when(estoqueUseCase.alertExpiringLots(7, 200)).thenReturn(3);

        service.alert();

        verify(estoqueUseCase).alertExpiringLots(eq(7), eq(200));
    }
}
