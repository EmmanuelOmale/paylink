package com.paylink.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.paylink.transfer.api.TransferRequest;

class RequestFingerprintTest {

    @Test
    void sameAmountDifferentScale() {
        String whole = RequestFingerprint.of(request("25000", "Rent share"));
        String withCents = RequestFingerprint.of(request("25000.00", "Rent share"));

        assertThat(whole).isEqualTo(withCents).hasSize(64);
    }

    @Test
    void differentRemarks() {
        String rent = RequestFingerprint.of(request("25000.00", "Rent share"));
        String dinner = RequestFingerprint.of(request("25000.00", "Dinner"));

        assertThat(rent).isNotEqualTo(dinner);
    }

    private static TransferRequest request(String amount, String remarks) {
        return new TransferRequest("W1001", "W2002", new BigDecimal(amount), "NGN", remarks);
    }
}
