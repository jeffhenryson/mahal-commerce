package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.CashbackUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashbackExpiryCleanupServiceTest {

    @Mock
    CashbackUseCase cashbackUseCase;

    @Test
    void cleanup_delegatesToUseCase_withConfiguredBatchSize() {
        CashbackExpiryCleanupService service = new CashbackExpiryCleanupService(cashbackUseCase, 200);
        when(cashbackUseCase.expireEntries(200)).thenReturn(3);

        service.cleanup();

        verify(cashbackUseCase).expireEntries(eq(200));
    }

    @Test
    void cleanup_doesNotThrow_whenNoEntriesAreExpired() {
        CashbackExpiryCleanupService service = new CashbackExpiryCleanupService(cashbackUseCase, 50);
        when(cashbackUseCase.expireEntries(50)).thenReturn(0);

        assertThatCode(service::cleanup).doesNotThrowAnyException();

        verify(cashbackUseCase).expireEntries(eq(50));
    }
}
