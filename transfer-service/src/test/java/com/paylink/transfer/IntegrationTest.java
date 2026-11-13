package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:${random.uuid};LOCK_TIMEOUT=10000",
                "paylink.rate-limit.max-per-window=1000"
        })
@Import(TestRiskConfiguration.class)
public abstract class IntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected ScriptedRiskClient risk;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetRisk() {
        risk.approve();
    }

    protected HttpResponse<String> postTransfer(String idempotencyKey, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/v1/transfers"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(request.build());
    }

    protected HttpResponse<String> getTransfer(String rrn) {
        return send(HttpRequest.newBuilder(uri("/api/v1/transfers/" + rrn)).GET().build());
    }

    protected static String transferBody(String source, String destination, String amount) {
        return transferBody(source, destination, amount, "NGN", "Test transfer");
    }

    protected static String transferBody(String source, String destination, String amount, String currency, String remarks) {
        return """
                {"sourceWalletId":"%s","destinationWalletId":"%s","amount":"%s","currency":"%s","remarks":"%s"}
                """.formatted(source, destination, amount, currency, remarks);
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    protected String field(HttpResponse<String> response, String name) {
        return jsonMapper.readTree(response.body()).path(name).asString();
    }

    protected boolean hasField(HttpResponse<String> response, String name) {
        return jsonMapper.readTree(response.body()).has(name);
    }

    protected String createWallet(String currency, String balance, Instant createdAt) {
        String id = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        jdbc.sql("INSERT INTO wallets (id, currency, status, balance, created_at) VALUES (:id, :currency, 'ACTIVE', :balance, :createdAt)")
                .param("id", id)
                .param("currency", currency)
                .param("balance", new BigDecimal(balance))
                .param("createdAt", createdAt)
                .update();
        jdbc.sql("INSERT INTO ledger_entries (wallet_id, entry_type, amount, created_at) VALUES (:id, 'CREDIT', :amount, :createdAt)")
                .param("id", id)
                .param("amount", new BigDecimal(balance))
                .param("createdAt", createdAt)
                .update();
        return id;
    }

    protected String createWallet(String balance) {
        return createWallet("NGN", balance, Instant.now().minus(Duration.ofDays(90)));
    }

    protected BigDecimal balanceOf(String walletId) {
        return jdbc.sql("SELECT balance FROM wallets WHERE id = :id").param("id", walletId).query(BigDecimal.class).single();
    }

    protected BigDecimal totalMoney() {
        return jdbc.sql("SELECT SUM(balance) FROM wallets").query(BigDecimal.class).single();
    }

    protected int ledgerRowsFor(String rrn) {
        return jdbc.sql("SELECT COUNT(*) FROM ledger_entries WHERE transfer_rrn = :rrn").param("rrn", rrn).query(Integer.class).single();
    }

    protected int transfersFrom(String walletId) {
        return jdbc.sql("SELECT COUNT(*) FROM transfers WHERE source_wallet_id = :id").param("id", walletId).query(Integer.class).single();
    }

    protected void assertLedgerMatchesBalances() {
        List<String> mismatches = jdbc.sql("""
                SELECT w.id FROM wallets w
                LEFT JOIN ledger_entries l ON l.wallet_id = w.id
                GROUP BY w.id, w.balance
                HAVING w.balance <> COALESCE(SUM(CASE l.entry_type WHEN 'CREDIT' THEN l.amount ELSE -l.amount END), 0)
                """)
                .query(String.class)
                .list();
        assertThat(mismatches).as("wallets whose balance differs from their ledger").isEmpty();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
