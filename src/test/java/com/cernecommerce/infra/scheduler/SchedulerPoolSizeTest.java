package com.cernecommerce.infra.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que {@code spring.task.scheduling.pool.size} está configurado (C019) — sem essa
 * property, o Spring Boot usa o default de 1 thread para todos os jobs {@code @Scheduled},
 * então jobs de limpeza com o mesmo horário (ex.: 3 deles às 03:45) executam em fila em vez
 * de em paralelo, atrasando uns aos outros.
 */
@SpringBootTest
@ActiveProfiles("dev")
class SchedulerPoolSizeTest {

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    void scheduler_pool_size_is_greater_than_default_of_one() {
        assertThat(taskScheduler.getPoolSize())
                .as("spring.task.scheduling.pool.size deve cobrir o maior grupo de jobs @Scheduled concorrentes (3 às 03:45)")
                .isGreaterThanOrEqualTo(4);
    }
}
