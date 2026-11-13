package com.paylink.banksearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrieBankSuggestionIndexTest {

    private static final BankSuggestion FIRST_BANK = new BankSuggestion("011", "First Bank of Nigeria");
    private static final BankSuggestion GTBANK = new BankSuggestion("058", "Guaranty Trust Bank");
    private static final BankSuggestion STANBIC = new BankSuggestion("221", "Stanbic IBTC Bank");
    private static final BankSuggestion ACCESS = new BankSuggestion("044", "Access Bank");
    private static final BankSuggestion ZENITH = new BankSuggestion("057", "Zenith Bank");

    private final TrieBankSuggestionIndex index = new TrieBankSuggestionIndex();

    @BeforeEach
    void setUp() {
        index.load(List.of(
                new Bank("011", "First Bank of Nigeria", List.of("FBN", "FirstBank"), 900),
                new Bank("058", "Guaranty Trust Bank", List.of("GTBank", "G.T.B"), 800),
                new Bank("221", "Stanbic IBTC Bank", List.of(), 500),
                new Bank("044", "Access Bank", null, 800),
                new Bank("057", "Zenith Bank", List.of("Zenith"), 700),
                new Bank("033", "United Bank for Africa", List.of("UBA"), 600)));
    }

    @Test
    void caseAndPunctuation() {
        List<BankSuggestion> dotted = index.suggest("G.T.B", 5);

        assertThat(dotted).containsExactly(GTBANK);
        assertThat(index.suggest("GTB", 5)).isEqualTo(dotted);
        assertThat(index.suggest("gtb", 5)).isEqualTo(dotted);
    }

    @Test
    void wordPrefixes() {
        assertThat(index.suggest("nigeria", 5)).containsExactly(FIRST_BANK);
        assertThat(index.suggest("bank of n", 5)).containsExactly(FIRST_BANK);
        assertThat(index.suggest("FBN", 5)).containsExactly(FIRST_BANK);
        assertThat(index.suggest("ibtc", 5)).containsExactly(STANBIC);
    }

    @Test
    void multiWordQuery() {
        assertThat(index.suggest("of nig", 5)).containsExactly(FIRST_BANK);
        assertThat(index.suggest("nig of", 5)).isEmpty();
    }

    @Test
    void blankQuery() {
        assertThat(index.suggest("", 5)).isEmpty();
        assertThat(index.suggest("   ", 5)).isEmpty();
        assertThat(index.suggest(" .. ", 5)).isEmpty();
    }

    @Test
    void limit() {
        assertThat(index.suggest("bank", 2)).hasSize(2);
        assertThat(index.suggest("bank", 0)).isEmpty();
    }

    @Test
    void noDuplicates() {
        assertThat(index.suggest("first", 5)).containsExactly(FIRST_BANK);
        assertThat(index.suggest("zenith", 5)).containsExactly(ZENITH);
    }

    @Test
    void popularityThenName() {
        assertThat(index.suggest("bank", 4)).containsExactly(FIRST_BANK, ACCESS, GTBANK, ZENITH);
    }

    @Test
    void selectionBreaksTie() {
        index.recordSelection("058");

        assertThat(index.suggest("bank", 3)).containsExactly(FIRST_BANK, GTBANK, ACCESS);
    }

    @Test
    void unknownCodeIgnored() {
        index.recordSelection("999");

        assertThat(index.suggest("bank", 3)).containsExactly(FIRST_BANK, ACCESS, GTBANK);
    }
}
