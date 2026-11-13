package com.paylink.transfer.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {

    private final JdbcClient jdbc;

    public LedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void recordTransfer(String rrn, String sourceWalletId, String destinationWalletId, BigDecimal amount, Instant at) {
        insertEntry(sourceWalletId, rrn, "DEBIT", amount, at);
        insertEntry(destinationWalletId, rrn, "CREDIT", amount, at);
    }

    private void insertEntry(String walletId, String rrn, String entryType, BigDecimal amount, Instant at) {
        jdbc.sql("""
                INSERT INTO ledger_entries (wallet_id, transfer_rrn, entry_type, amount, created_at)
                VALUES (:walletId, :rrn, :entryType, :amount, :at)
                """)
                .param("walletId", walletId)
                .param("rrn", rrn)
                .param("entryType", entryType)
                .param("amount", amount)
                .param("at", at)
                .update();
    }
}
