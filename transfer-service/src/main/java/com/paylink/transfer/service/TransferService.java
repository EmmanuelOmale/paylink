package com.paylink.transfer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paylink.transfer.api.ApiError;
import com.paylink.transfer.api.IdempotencyKeyReusedException;
import com.paylink.transfer.api.InvalidRequestException;
import com.paylink.transfer.api.RateLimitedException;
import com.paylink.transfer.api.RequestInProgressException;
import com.paylink.transfer.api.TransferNotFoundException;
import com.paylink.transfer.api.TransferRequest;
import com.paylink.transfer.api.TransferResponse;
import com.paylink.transfer.api.WalletNotFoundException;
import com.paylink.transfer.persistence.IdempotencyRepository;
import com.paylink.transfer.persistence.IdempotencyRow;
import com.paylink.transfer.persistence.LedgerRepository;
import com.paylink.transfer.persistence.RecentTransfers;
import com.paylink.transfer.persistence.TransferRepository;
import com.paylink.transfer.persistence.TransferRow;
import com.paylink.transfer.persistence.WalletRepository;
import com.paylink.transfer.persistence.WalletRow;
import com.paylink.transfer.risk.RiskClientException;
import com.paylink.transfer.risk.RiskDecision;
import com.paylink.transfer.risk.RiskScreeningClient;
import com.paylink.transfer.risk.RiskServiceUnavailableException;
import com.paylink.transfer.risk.RiskTimeoutException;
import com.paylink.transfer.rrn.RrnGenerator;
import com.paylink.transfer.support.RetryExecutor;

import tools.jackson.databind.json.JsonMapper;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private static final Pattern UUID_V4 =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RrnGenerator rrnGenerator;
    private final RetryExecutor retryExecutor;
    private final RiskScreeningClient riskClient;
    private final TransactionTemplate transactions;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final int maxTransfersPerWindow;
    private final Duration rateLimitWindow;
    private final Duration keyTtl;

    public TransferService(
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            LedgerRepository ledgerRepository,
            IdempotencyRepository idempotencyRepository,
            RrnGenerator rrnGenerator,
            RetryExecutor retryExecutor,
            RiskScreeningClient riskClient,
            TransactionTemplate transactions,
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${paylink.rate-limit.max-per-window}") int maxTransfersPerWindow,
            @Value("${paylink.rate-limit.window-seconds}") long rateLimitWindowSeconds,
            @Value("${paylink.idempotency.ttl-hours}") long keyTtlHours) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.ledgerRepository = ledgerRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.rrnGenerator = rrnGenerator;
        this.retryExecutor = retryExecutor;
        this.riskClient = riskClient;
        this.transactions = transactions;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.maxTransfersPerWindow = maxTransfersPerWindow;
        this.rateLimitWindow = Duration.ofSeconds(rateLimitWindowSeconds);
        this.keyTtl = Duration.ofHours(keyTtlHours);
    }

    public TransferResult transfer(String idempotencyKey, TransferRequest request) {
        if (idempotencyKey == null || !UUID_V4.matcher(idempotencyKey).matches()) {
            throw new InvalidRequestException("Idempotency-Key header must be a UUID v4");
        }
        request.validate();
        return claimAndProcess(idempotencyKey, request, RequestFingerprint.of(request), true);
    }

    public TransferRow find(String rrn) {
        return transferRepository.findByRrn(rrn).orElseThrow(() -> new TransferNotFoundException(rrn));
    }

    private TransferResult claimAndProcess(String key, TransferRequest request, String requestHash, boolean reclaimExpired) {
        TransferRow transfer;
        try {
            transfer = transactions.execute(status -> claimAndInitiate(key, request, requestHash));
        } catch (DuplicateKeyException e) {
            return answerExistingKey(key, request, requestHash, reclaimExpired);
        }
        if (transfer.status() == TransferStatus.FAILED) {
            return failureResult(transfer);
        }
        return screenAndSettle(transfer, key);
    }

    private TransferRow claimAndInitiate(String key, TransferRequest request, String requestHash) {
        idempotencyRepository.claim(request.sourceWalletId(), key, requestHash, clock.instant());
        WalletRow source = walletRepository.lockById(request.sourceWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.sourceWalletId()));
        WalletRow destination = walletRepository.findById(request.destinationWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.destinationWalletId()));
        requireUsable(source, request);
        requireUsable(destination, request);
        Instant now = clock.instant();
        enforceRateLimit(source.id(), now);
        TransferRow transfer = insertInitiated(request, now);
        idempotencyRepository.attachRrn(source.id(), key, transfer.rrn());
        if (source.holds(transfer.amount())) {
            return transfer;
        }
        return markFailed(transfer, key, FailureReason.INSUFFICIENT_FUNDS);
    }

    private TransferResult answerExistingKey(String key, TransferRequest request, String requestHash, boolean reclaimExpired) {
        String sourceWalletId = request.sourceWalletId();
        IdempotencyRow existing = transactions.execute(status -> idempotencyRepository.find(sourceWalletId, key))
                .orElseThrow(() -> new RequestInProgressException(null));
        Instant cutoff = clock.instant().minus(keyTtl);
        if (reclaimExpired && existing.createdAt().isBefore(cutoff)) {
            transactions.executeWithoutResult(status -> idempotencyRepository.deleteExpired(sourceWalletId, key, cutoff));
            return claimAndProcess(key, request, requestHash, false);
        }
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(existing.rrn());
        }
        if (existing.isInProgress()) {
            throw new RequestInProgressException(existing.rrn());
        }
        return new TransferResult(existing.httpStatus(), existing.responseBody());
    }

    private TransferResult screenAndSettle(TransferRow transfer, String key) {
        RiskDecision decision;
        try {
            decision = retryExecutor.call(() -> riskClient.screen(transfer.rrn(), transfer.sourceWalletId(),
                    transfer.destinationWalletId(), transfer.amount()), TransferService::isTransientRiskFailure);
        } catch (RiskTimeoutException | RiskServiceUnavailableException | RiskClientException e) {
            return failInTransaction(transfer, key, FailureReason.RISK_UNAVAILABLE);
        }
        if (decision.outcome() == RiskDecision.Outcome.DECLINED) {
            return failInTransaction(transfer, key, FailureReason.RISK_DECLINED);
        }
        TransferRow approved = transactions.execute(status -> moveStatus(transfer, TransferStatus.RISK_APPROVED, null));
        return transactions.execute(status -> settle(approved, key));
    }

    private TransferResult settle(TransferRow transfer, String key) {
        WalletRow source = lockBothWallets(transfer);
        if (!source.holds(transfer.amount())) {
            return failureResult(markFailed(transfer, key, FailureReason.INSUFFICIENT_FUNDS));
        }
        moveMoney(transfer);
        TransferRow completed = moveStatus(transfer, TransferStatus.COMPLETED, null);
        TransferResult result = new TransferResult(201, toJson(TransferResponse.from(completed)));
        finish(completed, key, result);
        return result;
    }

    private void moveMoney(TransferRow transfer) {
        if (walletRepository.debit(transfer.sourceWalletId(), transfer.amount()) != 1) {
            throw new IllegalStateException("Debit failed for " + transfer.rrn());
        }
        if (walletRepository.credit(transfer.destinationWalletId(), transfer.amount()) != 1) {
            throw new IllegalStateException("Credit failed for " + transfer.rrn());
        }
        ledgerRepository.recordTransfer(transfer.rrn(), transfer.sourceWalletId(),
                transfer.destinationWalletId(), transfer.amount(), clock.instant());
    }

    private WalletRow lockBothWallets(TransferRow transfer) {
        String source = transfer.sourceWalletId();
        String destination = transfer.destinationWalletId();
        boolean sourceFirst = source.compareTo(destination) < 0;
        WalletRow first = lockExistingWallet(sourceFirst ? source : destination);
        WalletRow second = lockExistingWallet(sourceFirst ? destination : source);
        return sourceFirst ? first : second;
    }

    private WalletRow lockExistingWallet(String walletId) {
        return walletRepository.lockById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));
    }

    private TransferResult failInTransaction(TransferRow transfer, String key, FailureReason reason) {
        return transactions.execute(status -> failureResult(markFailed(transfer, key, reason)));
    }

    private TransferRow markFailed(TransferRow transfer, String key, FailureReason reason) {
        TransferRow failed = moveStatus(transfer, TransferStatus.FAILED, reason);
        finish(failed, key, failureResult(failed));
        return failed;
    }

    private TransferRow moveStatus(TransferRow transfer, TransferStatus next, FailureReason reason) {
        transferRepository.moveStatus(transfer.rrn(), transfer.status(), next, reason, clock.instant());
        return transfer.withStatus(next, reason);
    }

    private void finish(TransferRow transfer, String key, TransferResult result) {
        idempotencyRepository.complete(transfer.sourceWalletId(), key, result.httpStatus(), result.json());
        String outcome = transfer.failureReason() == null ? "COMPLETED" : "FAILED " + transfer.failureReason();
        log.info("Transfer {} of {} {} from {} to {} ended {}", transfer.rrn(), transfer.amount(), transfer.currency(),
                transfer.sourceWalletId(), transfer.destinationWalletId(), outcome);
    }

    private TransferResult failureResult(TransferRow failed) {
        FailureReason reason = failed.failureReason();
        return new TransferResult(reason.httpStatus(), toJson(new ApiError(reason.name(), reason.message(), failed.rrn())));
    }

    private TransferRow insertInitiated(TransferRequest request, Instant now) {
        for (int attempt = 1; ; attempt++) {
            try {
                return insertWithNewRrn(request, now);
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    throw new IllegalStateException("RRN collision on retry", e);
                }
            }
        }
    }

    private TransferRow insertWithNewRrn(TransferRequest request, Instant now) {
        TransferRow transfer = new TransferRow(
                rrnGenerator.next(now),
                request.sourceWalletId(),
                request.destinationWalletId(),
                request.amount().setScale(2),
                request.currency().toUpperCase(Locale.ROOT),
                request.remarks() == null ? null : request.remarks().trim(),
                TransferStatus.INITIATED,
                null,
                now);
        transferRepository.insert(transfer);
        return transfer;
    }

    private void enforceRateLimit(String sourceWalletId, Instant now) {
        RecentTransfers recent = transferRepository.recentFrom(sourceWalletId, now.minus(rateLimitWindow));
        if (recent.count() < maxTransfersPerWindow) {
            return;
        }
        long millisUntilSlotFrees = Duration.between(now, recent.oldestCreatedAt().plus(rateLimitWindow)).toMillis();
        long retryAfterSeconds = Math.max(1, (millisUntilSlotFrees + 999) / 1000);
        throw new RateLimitedException(retryAfterSeconds);
    }

    private static void requireUsable(WalletRow wallet, TransferRequest request) {
        if (!wallet.isActive()) {
            throw new InvalidRequestException("Wallet " + wallet.id() + " is not active");
        }
        if (!wallet.currency().equalsIgnoreCase(request.currency())) {
            throw new InvalidRequestException("Wallet " + wallet.id() + " does not hold " + request.currency());
        }
    }

    private static boolean isTransientRiskFailure(RuntimeException e) {
        return e instanceof RiskTimeoutException || e instanceof RiskServiceUnavailableException;
    }

    private String toJson(Object body) {
        return jsonMapper.writeValueAsString(body);
    }

}
