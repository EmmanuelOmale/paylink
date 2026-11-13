package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class RiskScreeningIntegrationTest extends IntegrationTest {

    @Test
    void riskTimeoutGives503() {
        risk.alwaysTimeout();
        String source = createWallet("50000.00");
        String destination = createWallet("1000.00");
        String key = newKey();
        String body = transferBody(source, destination, "1000.00");

        HttpResponse<String> response = postTransfer(key, body);
        String rrn = field(response, "rrn");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(field(response, "code")).isEqualTo("RISK_UNAVAILABLE");
        assertThat(risk.attempts()).isEqualTo(3);
        assertThat(field(getTransfer(rrn), "status")).isEqualTo("FAILED");
        assertThat(ledgerRowsFor(rrn)).isZero();
        assertThat(balanceOf(source)).isEqualByComparingTo("50000.00");

        HttpResponse<String> replay = postTransfer(key, body);

        assertThat(replay.statusCode()).isEqualTo(503);
        assertThat(replay.body()).isEqualTo(response.body());
        assertThat(field(replay, "rrn")).isEqualTo(rrn);
        assertThat(risk.attempts()).isEqualTo(3);
    }

    @Test
    void newWalletHighValueDeclined() {
        risk.decline();
        String source = createWallet("NGN", "700000.00", Instant.now().minus(Duration.ofDays(2)));
        String destination = createWallet("1000.00");

        HttpResponse<String> response = postTransfer(newKey(), transferBody(source, destination, "600000.00"));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(field(response, "code")).isEqualTo("RISK_DECLINED");
        assertThat(risk.attempts()).isEqualTo(1);
        assertThat(balanceOf(source)).isEqualByComparingTo("700000.00");
    }
}
