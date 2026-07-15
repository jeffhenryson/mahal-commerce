package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogsServiceTest {

    @Mock
    AuditLogRepository repository;

    @InjectMocks
    AuditLogsService service;

    @Test
    void list_delega_para_repositorio_com_todos_os_filtros() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-12-31T23:59:59Z");
        PageResult<AuditLogEntry> expected = new PageResult<>(List.of(), 0, 10, 0L, 0);
        when(repository.findFiltered("user1", "USER_LOGGED_IN", from, to, 0, 10, Set.of())).thenReturn(expected);

        PageResult<AuditLogEntry> result = service.list("user1", "USER_LOGGED_IN", from, to, 0, 10, Set.of());

        assertThat(result).isEqualTo(expected);
        verify(repository).findFiltered("user1", "USER_LOGGED_IN", from, to, 0, 10, Set.of());
    }

    @Test
    void list_sem_filtros_delega_nulls_para_repositorio() {
        PageResult<AuditLogEntry> expected = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(repository.findFiltered(null, null, null, null, 0, 20, Set.of())).thenReturn(expected);

        PageResult<AuditLogEntry> result = service.list(null, null, null, null, 0, 20, Set.of());

        assertThat(result).isEqualTo(expected);
        verify(repository).findFiltered(null, null, null, null, 0, 20, Set.of());
    }

    @Test
    void list_retorna_pagina_com_entradas() {
        AuditLogEntry entry = new AuditLogEntry(1L, "admin", "USER_LOGGED_IN", null, null, "127.0.0.1",
                Instant.parse("2026-06-01T10:00:00Z"));
        PageResult<AuditLogEntry> expected = new PageResult<>(List.of(entry), 0, 10, 1L, 1);
        when(repository.findFiltered("admin", null, null, null, 0, 10, Set.of())).thenReturn(expected);

        PageResult<AuditLogEntry> result = service.list("admin", null, null, null, 0, 10, Set.of());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).username()).isEqualTo("admin");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void list_pagina_alem_do_total_retorna_conteudo_vazio() {
        PageResult<AuditLogEntry> empty = new PageResult<>(List.of(), 5, 10, 3L, 1);
        when(repository.findFiltered(null, null, null, null, 5, 10, Set.of())).thenReturn(empty);

        PageResult<AuditLogEntry> result = service.list(null, null, null, null, 5, 10, Set.of());

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(3L);
    }
}
