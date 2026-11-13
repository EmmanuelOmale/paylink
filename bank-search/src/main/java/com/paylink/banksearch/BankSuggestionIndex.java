package com.paylink.banksearch;

import java.util.List;

public interface BankSuggestionIndex {
    void load(List<Bank> banks);
    List<BankSuggestion> suggest(String query, int limit);
    void recordSelection(String bankCode);
}
