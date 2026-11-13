package com.paylink.transfer.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    private final JdbcClient jdbc;

    public IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void claim(String sourceWalletId, String key, String requestHash, Instant at) {
        jdbc.sql("""
                INSERT INTO idempotency_keys (source_wallet_id, idempotency_key, request_hash, status, created_at)
                VALUES (:source, :key, :hash, 'IN_PROGRESS', :at)
                """)
                .param("source", sourceWalletId)
                .param("key", key)
                .param("hash", requestHash)
                .param("at", at)
                .update();
    }

    public void attachRrn(String sourceWalletId, String key, String rrn) {
        int updated = jdbc.sql("UPDATE idempotency_keys SET rrn = :rrn WHERE source_wallet_id = :source AND idempotency_key = :key")
                .param("rrn", rrn)
                .param("source", sourceWalletId)
                .param("key", key)
                .update();
        requireSingleRow(updated, key);
    }

    public void complete(String sourceWalletId, String key, int httpStatus, String responseBody) {
        int updated = jdbc.sql("""
                UPDATE idempotency_keys SET status = 'COMPLETED', http_status = :httpStatus, response_body = :body
                WHERE source_wallet_id = :source AND idempotency_key = :key AND status = 'IN_PROGRESS'
                """)
                .param("httpStatus", httpStatus)
                .param("body", responseBody)
                .param("source", sourceWalletId)
                .param("key", key)
                .update();
        requireSingleRow(updated, key);
    }

    public Optional<IdempotencyRow> find(String sourceWalletId, String key) {
        return jdbc.sql("""
                SELECT request_hash, status, rrn, http_status, response_body, created_at
                FROM idempotency_keys WHERE source_wallet_id = :source AND idempotency_key = :key
                """)
                .param("source", sourceWalletId)
                .param("key", key)
                .query((rs, rowNum) -> new IdempotencyRow(
                        rs.getString("request_hash"),
                        rs.getString("status"),
                        rs.getString("rrn"),
                        rs.getObject("http_status", Integer.class),
                        rs.getString("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    public void deleteExpired(String sourceWalletId, String key, Instant cutoff) {
        jdbc.sql("DELETE FROM idempotency_keys WHERE source_wallet_id = :source AND idempotency_key = :key AND created_at < :cutoff")
                .param("source", sourceWalletId)
                .param("key", key)
                .param("cutoff", cutoff)
                .update();
    }

    private static void requireSingleRow(int updated, String key) {
        if (updated != 1) {
            throw new IllegalStateException("Idempotency key " + key + " was not in the expected state");
        }
    }
}
