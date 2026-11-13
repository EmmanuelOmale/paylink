package com.paylink.banksearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class TrieBankSuggestionIndexLatencyTest {

    private static final String[] PREFIXES = {"Ade", "Bola", "Chi", "Dayo", "Eko", "Femi", "Gbenga", "Ife", "Kemi", "Lagos"};

    private final Random random = new Random(42);

    @Test
    void p99UnderOneMillisecond() {
        TrieBankSuggestionIndex index = new TrieBankSuggestionIndex();
        index.load(banks());
        List<String> queries = queries();

        for (int i = 0; i < 20_000; i++) {
            index.suggest(queries.get(i % queries.size()), 10);
        }
        long[] times = new long[100_000];
        for (int i = 0; i < times.length; i++) {
            String query = queries.get(i % queries.size());
            long start = System.nanoTime();
            index.suggest(query, 10);
            times[i] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        long p99 = times[98_999];

        System.out.println("p99: " + p99 / 1000 + " us");
        assertThat(p99).isLessThan(1_000_000);
    }

    private List<Bank> banks() {
        List<Bank> banks = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            String word = name(i);
            String type = i < 900 ? " Microfinance Bank" : " Trust Bank";
            banks.add(new Bank("B" + i, word + type, List.of(word + "MFB"), random.nextInt(100_000)));
        }
        return banks;
    }

    private List<String> queries() {
        List<String> queries = new ArrayList<>(List.of("micro", "microfinance", "microfinance bank", "bank", "trust b", "m", "b"));
        for (int i = 0; i < 200; i++) {
            String word = name(random.nextInt(1200)).toLowerCase();
            queries.add(word.substring(0, 1 + random.nextInt(word.length())));
            queries.add(word + " micro");
        }
        return queries;
    }

    private static String name(int i) {
        return PREFIXES[i % PREFIXES.length] + i;
    }
}
