package com.paylink.transfer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.paylink.transfer.risk.RiskDecision;
import com.paylink.transfer.risk.RiskScreeningClient;
import com.paylink.transfer.risk.RiskTimeoutException;

public class ScriptedRiskClient implements RiskScreeningClient {

    private final AtomicInteger attempts = new AtomicInteger();
    private volatile Supplier<RiskDecision> script = ScriptedRiskClient::approved;

    @Override
    public RiskDecision screen(String rrn, String sourceWalletId, String destinationWalletId, BigDecimal amount) {
        attempts.incrementAndGet();
        return script.get();
    }

    public void approve() {
        attempts.set(0);
        script = ScriptedRiskClient::approved;
    }

    public void slowApprove(Duration delay) {
        attempts.set(0);
        script = () -> {
            pause(delay);
            return approved();
        };
    }

    public void alwaysTimeout() {
        attempts.set(0);
        script = () -> {
            throw new RiskTimeoutException();
        };
    }

    public void decline() {
        attempts.set(0);
        script = () -> new RiskDecision(RiskDecision.Outcome.DECLINED, "NEW_WALLET_HIGH_VALUE");
    }

    public int attempts() {
        return attempts.get();
    }

    private static RiskDecision approved() {
        return new RiskDecision(RiskDecision.Outcome.APPROVED, null);
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
