package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.support.BugReport;
import com.cernecommerce.core.ports.out.support.BugReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugReportServiceTest {

    @Mock BugReportRepository bugReportRepository;

    BugReportService service;

    @BeforeEach
    void setUp() {
        service = new BugReportService(bugReportRepository);
    }

    @Test
    void createBugReport_savesReportAssociatedToReporter() {
        when(bugReportRepository.save(any())).thenAnswer(inv -> {
            BugReport toSave = inv.getArgument(0);
            return new BugReport(1L, toSave.reportedBy(), toSave.title(), toSave.description(),
                    toSave.pageUrl(), toSave.userAgent(), toSave.createdAt());
        });

        BugReport result = service.createBugReport("alice", "Botão quebrado", "Ao clicar nada acontece",
                "/app/pedidos", "Mozilla/5.0");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.reportedBy()).isEqualTo("alice");
        assertThat(result.title()).isEqualTo("Botão quebrado");
        verify(bugReportRepository).save(any());
    }
}
