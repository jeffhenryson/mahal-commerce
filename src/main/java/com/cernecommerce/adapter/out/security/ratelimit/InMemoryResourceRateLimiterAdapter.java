package com.cernecommerce.adapter.out.security.ratelimit;

import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.ports.out.ratelimit.ResourceRateLimiterPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("dev")
public class InMemoryResourceRateLimiterAdapter implements ResourceRateLimiterPort {

    // PLAT-C030 / cobertura de testes de segurança — buckets fixos por recurso sensível.
    // crm-export: exportação de PII é rara e não deve ser repetida em massa por um mesmo usuário.
    // estoque-movements: listagem interna, limite generoso para não atrapalhar uso legítimo do PDV/admin.
    // shop-catalog: endpoint público sem autenticação — chave é o IP, protege contra scraping.
    private static final Map<String, BucketPolicy> POLICIES = Map.of(
            "crm-export", new BucketPolicy(3600, 5),
            "estoque-movements", new BucketPolicy(60, 60),
            "shop-catalog", new BucketPolicy(60, 120));

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    public void checkOrThrow(String bucket, String key) {
        BucketPolicy policy = POLICIES.getOrDefault(bucket, new BucketPolicy(60, 30));
        long now = Instant.now().getEpochSecond();
        long cutoff = now - policy.windowSeconds();
        Deque<Long> queue = hits.computeIfAbsent(bucket + ":" + key, k -> new ArrayDeque<>());

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
                queue.removeFirst();
            }
            if (queue.size() >= policy.maxRequests()) {
                throw new RateLimitExceededException(bucket, policy.windowSeconds());
            }
            queue.addLast(now);
        }
    }

    public void reset() {
        hits.clear();
    }

    private record BucketPolicy(long windowSeconds, int maxRequests) {
    }
}
