package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class IdempotencyIntegrationTest extends IntegrationTest {

    @Test
    void sameKeySameBodyReplays() {
        String source = createWallet("50000.00");
        String destination = createWallet("1000.00");
        String key = newKey();
        String body = transferBody(source, destination, "25000.00");

        HttpResponse<String> first = postTransfer(key, body);
        HttpResponse<String> replay = postTransfer(key, body);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(replay.statusCode()).isEqualTo(first.statusCode());
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(field(replay, "rrn")).isEqualTo(field(first, "rrn"));
        assertThat(balanceOf(source)).isEqualByComparingTo("25000.00");
        assertThat(ledgerRowsFor(field(first, "rrn"))).isEqualTo(2);
    }

    @Test
    void sameKeyDifferentBodyRejected() {
        String source = createWallet("50000.00");
        String destination = createWallet("1000.00");
        String key = newKey();

        HttpResponse<String> first = postTransfer(key, transferBody(source, destination, "25000.00"));
        HttpResponse<String> reused = postTransfer(key, transferBody(source, destination, "26000.00"));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(reused.statusCode()).isEqualTo(422);
        assertThat(field(reused, "code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(transfersFrom(source)).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyProcessedOnce() throws Exception {
        risk.slowApprove(Duration.ofMillis(300));
        String source = createWallet("50000.00");
        String destination = createWallet("1000.00");
        String key = newKey();
        String body = transferBody(source, destination, "1000.00");
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<String>> first = pool.submit(() -> postAfter(start, key, body));
            Future<HttpResponse<String>> second = pool.submit(() -> postAfter(start, key, body));
            start.countDown();
            statuses = List.of(first.get().statusCode(), second.get().statusCode());
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(balanceOf(source)).isEqualByComparingTo("49000.00");
        assertThat(transfersFrom(source)).isEqualTo(1);
    }

    private HttpResponse<String> postAfter(CountDownLatch start, String key, String body) throws InterruptedException {
        start.await();
        return postTransfer(key, body);
    }
}
