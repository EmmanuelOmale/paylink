package com.paylink.transfer.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.paylink.transfer.service.FailureReason;
import com.paylink.transfer.service.TransferStatus;

@Repository
public class TransferRepository {

    private final JdbcClient jdbc;

    public TransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(TransferRow transfer) {
        jdbc.sql("""
                INSERT INTO transfers (rrn, source_wallet_id, destination_wallet_id, amount, currency, remarks,
                                       status, created_at, updated_at)
                VALUES (:rrn, :source, :destination, :amount, :currency, :remarks, :status, :createdAt, :createdAt)
                """)
                .param("rrn", transfer.rrn())
                .param("source", transfer.sourceWalletId())
                .param("destination", transfer.destinationWalletId())
                .param("amount", transfer.amount())
                .param("currency", transfer.currency())
                .param("remarks", transfer.remarks())
                .param("status", transfer.status().name())
                .param("createdAt", transfer.createdAt())
                .update();
    }

    public Optional<TransferRow> findByRrn(String rrn) {
        return jdbc.sql("""
                SELECT rrn, source_wallet_id, destination_wallet_id, amount, currency, remarks,
                       status, failure_reason, created_at
                FROM transfers WHERE rrn = :rrn
                """)
                .param("rrn", rrn)
                .query(TransferRepository::mapTransfer)
                .optional();
    }

    public void moveStatus(String rrn, TransferStatus expected, TransferStatus next, FailureReason reason, Instant at) {
        if (!expected.canMoveTo(next)) {
            throw new IllegalStateException("Transfer " + rrn + " cannot move from " + expected + " to " + next);
        }
        int updated = jdbc.sql("""
                UPDATE transfers SET status = :next, failure_reason = :reason, updated_at = :at
                WHERE rrn = :rrn AND status = :expected
                """)
                .param("next", next.name())
                .param("reason", reason == null ? null : reason.name())
                .param("at", at)
                .param("rrn", rrn)
                .param("expected", expected.name())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Transfer " + rrn + " was not in status " + expected);
        }
    }

    public RecentTransfers recentFrom(String sourceWalletId, Instant since) {
        return jdbc.sql("""
                SELECT COUNT(*) AS transfer_count, MIN(created_at) AS oldest
                FROM transfers WHERE source_wallet_id = :source AND created_at > :since
                """)
                .param("source", sourceWalletId)
                .param("since", since)
                .query((rs, rowNum) -> new RecentTransfers(rs.getInt("transfer_count"), toInstant(rs, "oldest")))
                .single();
    }

    private static TransferRow mapTransfer(ResultSet rs, int rowNum) throws SQLException {
        String failureReason = rs.getString("failure_reason");
        return new TransferRow(
                rs.getString("rrn"),
                rs.getString("source_wallet_id"),
                rs.getString("destination_wallet_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("remarks"),
                TransferStatus.valueOf(rs.getString("status")),
                failureReason == null ? null : FailureReason.valueOf(failureReason),
                toInstant(rs, "created_at"));
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
