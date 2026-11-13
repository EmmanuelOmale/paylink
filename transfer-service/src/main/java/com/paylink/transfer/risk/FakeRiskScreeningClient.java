package com.paylink.transfer.risk;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.paylink.transfer.persistence.WalletRepository;
import com.paylink.transfer.support.Sleeper;

@Component
public class FakeRiskScreeningClient implements RiskScreeningClient {

    private static final BigDecimal HIGH_VALUE = new BigDecimal("500000.00");
    private static final Duration NEW_WALLET_AGE = Duration.ofDays(7);

    private final WalletRepository walletRepository;
    private final Clock clock;
    private final Sleeper sleeper;

    public FakeRiskScreeningClient(WalletRepository walletRepository, Clock clock, Sleeper sleeper) {
        this.walletRepository = walletRepository;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public RiskDecision screen(String rrn, String sourceWalletId, String destinationWalletId, BigDecimal amount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        sleeper.sleep(Duration.ofMillis(random.nextLong(50, 401)));
        int roll = random.nextInt(100);
        if (roll < 10) {
            throw new RiskTimeoutException();
        }
        if (roll < 20) {
            throw new RiskServiceUnavailableException(503);
        }
        if (amount.compareTo(HIGH_VALUE) > 0 && isNewWallet(sourceWalletId)) {
            return new RiskDecision(RiskDecision.Outcome.DECLINED, "NEW_WALLET_HIGH_VALUE");
        }
        return new RiskDecision(RiskDecision.Outcome.APPROVED, null);
    }

    private boolean isNewWallet(String walletId) {
        Instant newWalletCutoff = clock.instant().minus(NEW_WALLET_AGE);
        return walletRepository.findById(walletId)
                .map(wallet -> wallet.createdAt().isAfter(newWalletCutoff))
                .orElse(false);
    }
}
