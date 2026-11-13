package com.paylink.transfer.support;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final Sleeper sleeper;
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    public RetryExecutor(
            Sleeper sleeper,
            @Value("${paylink.risk.max-attempts}") int maxAttempts,
            @Value("${paylink.risk.base-backoff-ms}") long baseBackoffMs,
            @Value("${paylink.risk.max-backoff-ms}") long maxBackoffMs) {
        this.sleeper = sleeper;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    public <T> T call(Supplier<T> action, Predicate<RuntimeException> retryable) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts || !retryable.test(e)) {
                    throw e;
                }
                long backoffMs = backoffAfter(attempt);
                log.warn("Attempt {}/{} failed: {}. Retrying in {} ms", attempt, maxAttempts, e.getMessage(), backoffMs);
                sleeper.sleep(Duration.ofMillis(backoffMs));
            }
        }
    }

    private long backoffAfter(int failedAttempt) {
        long exponential = baseBackoffMs << Math.min(failedAttempt - 1, 30);
        long ceiling = Math.min(maxBackoffMs, exponential);
        return ThreadLocalRandom.current().nextLong(ceiling + 1);
    }
}
