package com.cernecommerce.core.domain.exception.ratelimit;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String bucket, long retryAfterSeconds) {
        super("Muitas requisições para o recurso: " + bucket);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
