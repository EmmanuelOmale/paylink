package com.paylink.transfer.persistence;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletRow(String id, String currency, String status, BigDecimal balance, Instant createdAt) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean holds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
