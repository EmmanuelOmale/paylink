package com.paylink.transfer.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RetryExecutorTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final RetryExecutor retryExecutor = new RetryExecutor(sleeps::add, 3, 100, 1000);
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void succeedsOnThirdAttempt() {
        String outcome = retryExecutor.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "done";
        }, e -> true);

        assertThat(outcome).isEqualTo("done");
        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void givesUpAfterThreeAttempts() {
        assertThatThrownBy(() -> retryExecutor.call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("still down");
        }, e -> true)).isInstanceOf(IllegalStateException.class).hasMessage("still down");

        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(0)).isBetween(Duration.ZERO, Duration.ofMillis(100));
        assertThat(sleeps.get(1)).isBetween(Duration.ZERO, Duration.ofMillis(200));
    }

    @Test
    void nonRetryableNotRetried() {
        assertThatThrownBy(() -> retryExecutor.call(() -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("declined");
        }, e -> e instanceof IllegalStateException)).isInstanceOf(IllegalArgumentException.class);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }
}
