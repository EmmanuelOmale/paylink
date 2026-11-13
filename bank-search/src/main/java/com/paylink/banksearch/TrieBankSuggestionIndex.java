package com.paylink.banksearch;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.regex.Pattern;

public final class TrieBankSuggestionIndex implements BankSuggestionIndex {

    private static final Pattern DROPPED_PUNCTUATION = Pattern.compile("[.'’]");
    private static final Pattern WORD_SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private volatile Snapshot current = build(List.of());

    @Override
    public synchronized void load(List<Bank> banks) {
        current = build(banks);
    }

    @Override
    public List<BankSuggestion> suggest(String query, int limit) {
        Snapshot snapshot = current;
        String normalized = normalize(query);
        if (normalized.isEmpty() || limit <= 0) {
            return List.of();
        }
        Node match = snapshot.root().find(normalized);
        if (match == null) {
            return List.of();
        }
        return topRanked(snapshot, match.bankIndexes(), limit);
    }

    @Override
    public void recordSelection(String bankCode) {
        Snapshot snapshot = current;
        Integer index = snapshot.indexByCode().get(bankCode);
        if (index != null) {
            snapshot.popularity().incrementAndGet(index);
        }
    }

    private static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String joined = DROPPED_PUNCTUATION.matcher(lower).replaceAll("");
        return WORD_SEPARATORS.matcher(joined).replaceAll(" ").trim();
    }

    private static Snapshot build(List<Bank> banks) {
        Node root = new Node();
        Bank[] byIndex = banks.toArray(Bank[]::new);
        AtomicLongArray popularity = new AtomicLongArray(byIndex.length);
        Map<String, Integer> indexByCode = new HashMap<>();
        for (int index = 0; index < byIndex.length; index++) {
            Bank bank = byIndex[index];
            popularity.set(index, bank.popularity());
            indexByCode.put(bank.bankCode(), index);
            indexNames(root, bank, index);
        }
        root.freeze();
        return new Snapshot(root, byIndex, popularity, indexByCode);
    }

    private static void indexNames(Node root, Bank bank, int bankIndex) {
        indexWordSuffixes(root, normalize(bank.displayName()), bankIndex);
        List<String> aliases = bank.aliases() == null ? List.of() : bank.aliases();
        for (String alias : aliases) {
            indexWordSuffixes(root, normalize(alias), bankIndex);
        }
    }

    private static void indexWordSuffixes(Node root, String name, int bankIndex) {
        if (name.isEmpty()) {
            return;
        }
        int wordStart = 0;
        while (wordStart >= 0) {
            root.insert(name, wordStart, bankIndex);
            int space = name.indexOf(' ', wordStart);
            wordStart = space < 0 ? -1 : space + 1;
        }
    }

    private static List<BankSuggestion> topRanked(Snapshot snapshot, int[] candidates, int limit) {
        long[] popularity = new long[candidates.length];
        for (int position = 0; position < candidates.length; position++) {
            popularity[position] = snapshot.popularity().get(candidates[position]);
        }
        Comparator<Integer> byRank = rankOrder(snapshot.banks(), candidates, popularity);
        PriorityQueue<Integer> worstFirst = new PriorityQueue<>(limit + 1, byRank.reversed());
        for (int position = 0; position < candidates.length; position++) {
            if (worstFirst.size() < limit) {
                worstFirst.offer(position);
            } else if (byRank.compare(position, worstFirst.peek()) < 0) {
                worstFirst.poll();
                worstFirst.offer(position);
            }
        }
        return inRankOrder(snapshot.banks(), candidates, worstFirst);
    }

    private static Comparator<Integer> rankOrder(Bank[] banks, int[] candidates, long[] popularity) {
        Comparator<Integer> byPopularity = Comparator.comparingLong(position -> popularity[position]);
        return byPopularity.reversed()
                .thenComparing(position -> banks[candidates[position]].displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(position -> banks[candidates[position]].bankCode());
    }

    private static List<BankSuggestion> inRankOrder(Bank[] banks, int[] candidates, PriorityQueue<Integer> worstFirst) {
        BankSuggestion[] ranked = new BankSuggestion[worstFirst.size()];
        for (int slot = ranked.length - 1; slot >= 0; slot--) {
            Bank bank = banks[candidates[worstFirst.poll()]];
            ranked[slot] = new BankSuggestion(bank.bankCode(), bank.displayName());
        }
        return List.of(ranked);
    }

    private record Snapshot(Node root, Bank[] banks, AtomicLongArray popularity, Map<String, Integer> indexByCode) {}

    private static final class Node {

        private static final int[] NO_BANKS = new int[0];

        private final Map<Character, Node> children = new HashMap<>();
        private BitSet pendingBanks = new BitSet();
        private int[] bankIndexes = NO_BANKS;

        void insert(String text, int from, int bankIndex) {
            Node node = this;
            for (int i = from; i < text.length(); i++) {
                node = node.children.computeIfAbsent(text.charAt(i), key -> new Node());
                node.pendingBanks.set(bankIndex);
            }
        }

        Node find(String prefix) {
            Node node = this;
            for (int i = 0; i < prefix.length() && node != null; i++) {
                node = node.children.get(prefix.charAt(i));
            }
            return node;
        }

        int[] bankIndexes() {
            return bankIndexes;
        }

        void freeze() {
            bankIndexes = pendingBanks.stream().toArray();
            pendingBanks = null;
            for (Node child : children.values()) {
                child.freeze();
            }
        }
    }
}
