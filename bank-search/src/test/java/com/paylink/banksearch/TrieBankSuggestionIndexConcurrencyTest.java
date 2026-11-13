package com.paylink.banksearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class TrieBankSuggestionIndexConcurrencyTest {

    private static final BankSuggestion ACCESS = new BankSuggestion("044", "Access Bank");

    private final TrieBankSuggestionIndex index = new TrieBankSuggestionIndex();

    @Test
    void concurrentSelections() throws Exception {
        index.load(List.of(
                new Bank("001", "Alpha Bank", List.of(), 7999),
                new Bank("002", "Kappa Bank", List.of(), 0),
                new Bank("003", "Zeta Bank", List.of(), 8001)));

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                workers.add(pool.submit(() -> {
                    for (int selection = 0; selection < 1000; selection++) {
                        index.recordSelection("002");
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        assertThat(index.suggest("bank", 3)).extracting(BankSuggestion::displayName)
                .containsExactly("Zeta Bank", "Kappa Bank", "Alpha Bank");
    }

    @Test
    void reloadDuringQueries() throws Exception {
        List<Bank> small = List.of(new Bank("044", "Access Bank", List.of("Access"), 10));
        List<Bank> large = List.of(
                new Bank("044", "Access Bank", List.of("Access", "Diamond"), 10),
                new Bank("058", "Guaranty Trust Bank", List.of("GTB"), 20),
                new Bank("057", "Zenith Bank", List.of(), 30));
        index.load(small);
        AtomicBoolean reloading = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> readers = new ArrayList<>();
            for (int reader = 0; reader < 4; reader++) {
                readers.add(pool.submit(() -> queryUntilStopped(reloading)));
            }
            for (int load = 0; load < 100; load++) {
                index.load(load % 2 == 0 ? large : small);
            }
            reloading.set(false);
            for (Future<Integer> reader : readers) {
                assertThat(reader.get()).isPositive();
            }
        }
    }

    private int queryUntilStopped(AtomicBoolean reloading) {
        int queries = 0;
        while (reloading.get() || queries == 0) {
            assertThat(index.suggest("access", 5)).containsExactly(ACCESS);
            queries++;
        }
        return queries;
    }
}
