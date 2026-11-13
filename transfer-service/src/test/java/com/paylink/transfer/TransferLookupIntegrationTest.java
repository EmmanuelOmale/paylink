package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

class TransferLookupIntegrationTest extends IntegrationTest {

    @Test
    void getFailedTransfer() {
        String source = createWallet("100.00");
        String destination = createWallet("100.00");
        HttpResponse<String> created = postTransfer(newKey(), transferBody(source, destination, "500.00"));
        String rrn = field(created, "rrn");

        HttpResponse<String> found = getTransfer(rrn);

        assertThat(created.statusCode()).isEqualTo(422);
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(field(found, "rrn")).isEqualTo(rrn);
        assertThat(field(found, "status")).isEqualTo("FAILED");
        assertThat(field(found, "failureReason")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(field(found, "amount")).isEqualTo("500.00");
    }

    @Test
    void getUnknownRrn() {
        HttpResponse<String> response = getTransfer("999999999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(field(response, "code")).isEqualTo("TRANSFER_NOT_FOUND");
        assertThat(hasField(response, "rrn")).isFalse();
    }
}
