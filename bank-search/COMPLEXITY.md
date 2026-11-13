# Complexity

S is the number of characters across all indexed suffixes, L is the query length, m is the number of matching banks and k is the limit.

**load** is O(S). Every suffix that starts at a word is walked into the trie one character at a time. Turning each node's BitSet into an int[] at the end is also bounded by S.

**suggest** is O(L + m log k). Finding the node takes L steps. Then we copy the m candidates' popularity and keep the best k in a small heap. Even "micro", which matches about 900 banks, takes tens of microseconds.

**recordSelection** is O(1): one map lookup and one atomic increment.

## Memory

About 7.5 MB for 1,200 banks with two aliases each. We measured it by comparing heap use before and after `load`. Banks that end in "Microfinance Bank" share most of their trie path, so real data stays well below the 50 MB limit.

## Latency

p99 was 24 to 37 µs across runs with 1,200 synthetic banks (900 of them microfinance banks), after 20,000 warm-up queries and 100,000 timed ones, on JDK 21 on a Windows laptop.

## Why the node lists are not sorted

If every node kept its banks sorted by popularity, `suggest` could just read the first k and become O(L + k). The catch is that popularity changes on every selection, and each bank sits on roughly 50 nodes. Every `recordSelection` would then have to re-sort about 50 lists under a write lock that readers would wait on. Ranking at query time keeps reads lock-free and selections O(1), and it is still far below 1 ms.

## Thread safety

`load` builds a whole new snapshot and then swaps it in with one volatile write, so a reader sees either the old index or the new one. `suggest` copies popularity before sorting, so increments happening at the same time can't confuse the comparator.
