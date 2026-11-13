package com.paylink.transfer.persistence;

import java.time.Instant;

public record RecentTransfers(int count, Instant oldestCreatedAt) {}
