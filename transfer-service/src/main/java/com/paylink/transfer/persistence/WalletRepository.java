package com.paylink.transfer.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {

    private static final String SELECT_WALLET = "SELECT id, currency, status, balance, created_at FROM wallets WHERE id = :id";

    private final JdbcClient jdbc;

    public WalletRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<WalletRow> findById(String id) {
        return jdbc.sql(SELECT_WALLET).param("id", id).query(WalletRepository::mapWallet).optional();
    }

    public Optional<WalletRow> lockById(String id) {
        return jdbc.sql(SELECT_WALLET + " FOR UPDATE").param("id", id).query(WalletRepository::mapWallet).optional();
    }

    public int debit(String id, BigDecimal amount) {
        return jdbc.sql("UPDATE wallets SET balance = balance - :amount WHERE id = :id AND balance >= :amount")
                .param("amount", amount)
                .param("id", id)
                .update();
    }

    public int credit(String id, BigDecimal amount) {
        return jdbc.sql("UPDATE wallets SET balance = balance + :amount WHERE id = :id")
                .param("amount", amount)
                .param("id", id)
                .update();
    }

    private static WalletRow mapWallet(ResultSet rs, int rowNum) throws SQLException {
        return new WalletRow(
                rs.getString("id"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getBigDecimal("balance"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
