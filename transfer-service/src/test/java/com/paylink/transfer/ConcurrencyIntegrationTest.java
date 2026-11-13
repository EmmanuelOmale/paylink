package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class ConcurrencyIntegrationTest extends IntegrationTest {

    @Test
    void fiftyConcurrentDebitsNeverOverdraw() throws Exception {
        String source = createWallet("10000.00");
        String destination = createWallet("1.00");
        BigDecimal moneyBefore = totalMoney();

        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            bodies.add(transferBody(source, destination, "1000.00"));
        }
        List<Integer> statuses = postAllAtOnce(bodies);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(10);
        assertThat(statuses).filteredOn(status -> status == 422).hasSize(40);
        assertThat(balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(totalMoney()).isEqualByComparingTo(moneyBefore);
        assertLedgerMatchesBalances();
    }

    @Test
    void oppositeTransfersNoDeadlock() {
        String walletA = createWallet("100000.00");
        String walletB = createWallet("100000.00");
        BigDecimal moneyBefore = totalMoney();

        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bodies.add(i % 2 == 0
                    ? transferBody(walletA, walletB, "100.00")
                    : transferBody(walletB, walletA, "100.00"));
        }
        List<Integer> statuses = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> postAllAtOnce(bodies));

        assertThat(statuses).containsOnly(201);
        assertThat(balanceOf(walletA)).isEqualByComparingTo("100000.00");
        assertThat(balanceOf(walletB)).isEqualByComparingTo("100000.00");
        assertThat(totalMoney()).isEqualByComparingTo(moneyBefore);
        assertLedgerMatchesBalances();
    }

    private List<Integer> postAllAtOnce(List<String> bodies) throws InterruptedException, ExecutionException {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(bodies.size())) {
            for (String body : bodies) {
                responses.add(pool.submit(() -> {
                    start.await();
                    return postTransfer(newKey(), body).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get());
            }
            return statuses;
        }
    }
}
