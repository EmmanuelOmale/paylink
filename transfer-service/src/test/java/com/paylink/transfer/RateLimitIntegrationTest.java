package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "paylink.rate-limit.max-per-window=5")
class RateLimitIntegrationTest extends IntegrationTest {

    @Test
    void sixthTransferRateLimited() {
        String source = createWallet("50000.00");
        String destination = createWallet("1000.00");
        String firstKey = newKey();
        String firstBody = transferBody(source, destination, "100.00");
        HttpResponse<String> first = postTransfer(firstKey, firstBody);
        for (int i = 0; i < 4; i++) {
            assertThat(postTransfer(newKey(), transferBody(source, destination, "100.00")).statusCode()).isEqualTo(201);
        }

        HttpResponse<String> sixth = postTransfer(newKey(), transferBody(source, destination, "100.00"));
        HttpResponse<String> replay = postTransfer(firstKey, firstBody);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(field(sixth, "code")).isEqualTo("RATE_LIMITED");
        assertThat(sixth.headers().firstValueAsLong("Retry-After").orElseThrow()).isBetween(1L, 60L);
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
    }
}
