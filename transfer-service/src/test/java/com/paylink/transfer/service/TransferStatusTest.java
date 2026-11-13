package com.paylink.transfer.service;

import static com.paylink.transfer.service.TransferStatus.COMPLETED;
import static com.paylink.transfer.service.TransferStatus.FAILED;
import static com.paylink.transfer.service.TransferStatus.INITIATED;
import static com.paylink.transfer.service.TransferStatus.RISK_APPROVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class TransferStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            INITIATED + "->" + RISK_APPROVED,
            INITIATED + "->" + FAILED,
            RISK_APPROVED + "->" + COMPLETED,
            RISK_APPROVED + "->" + FAILED);

    @Test
    void onlyFourTransitionsAllowed() {
        int checkedPairs = 0;
        for (TransferStatus from : TransferStatus.values()) {
            for (TransferStatus to : TransferStatus.values()) {
                assertThat(from.canMoveTo(to)).as(from + "->" + to).isEqualTo(ALLOWED.contains(from + "->" + to));
                checkedPairs++;
            }
        }
        assertThat(checkedPairs).isEqualTo(16);
    }
}
