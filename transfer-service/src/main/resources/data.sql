INSERT INTO wallets (id, currency, status, balance, created_at)
SELECT 'W1001', 'NGN', 'ACTIVE', 1000000.00, DATEADD('DAY', -90, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = 'W1001');

INSERT INTO wallets (id, currency, status, balance, created_at)
SELECT 'W2002', 'NGN', 'ACTIVE', 500000.00, DATEADD('DAY', -90, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = 'W2002');

INSERT INTO wallets (id, currency, status, balance, created_at)
SELECT 'W3003', 'NGN', 'ACTIVE', 2000000.00, DATEADD('DAY', -2, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = 'W3003');

INSERT INTO wallets (id, currency, status, balance, created_at)
SELECT 'W4004', 'NGN', 'SUSPENDED', 10000.00, DATEADD('DAY', -90, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = 'W4004');

INSERT INTO wallets (id, currency, status, balance, created_at)
SELECT 'W5005', 'USD', 'ACTIVE', 1000.00, DATEADD('DAY', -90, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM wallets WHERE id = 'W5005');

INSERT INTO ledger_entries (wallet_id, transfer_rrn, entry_type, amount, created_at)
SELECT 'W1001', NULL, 'CREDIT', 1000000.00, created_at FROM wallets
WHERE id = 'W1001' AND NOT EXISTS (SELECT 1 FROM ledger_entries WHERE wallet_id = 'W1001' AND transfer_rrn IS NULL);

INSERT INTO ledger_entries (wallet_id, transfer_rrn, entry_type, amount, created_at)
SELECT 'W2002', NULL, 'CREDIT', 500000.00, created_at FROM wallets
WHERE id = 'W2002' AND NOT EXISTS (SELECT 1 FROM ledger_entries WHERE wallet_id = 'W2002' AND transfer_rrn IS NULL);

INSERT INTO ledger_entries (wallet_id, transfer_rrn, entry_type, amount, created_at)
SELECT 'W3003', NULL, 'CREDIT', 2000000.00, created_at FROM wallets
WHERE id = 'W3003' AND NOT EXISTS (SELECT 1 FROM ledger_entries WHERE wallet_id = 'W3003' AND transfer_rrn IS NULL);

INSERT INTO ledger_entries (wallet_id, transfer_rrn, entry_type, amount, created_at)
SELECT 'W4004', NULL, 'CREDIT', 10000.00, created_at FROM wallets
WHERE id = 'W4004' AND NOT EXISTS (SELECT 1 FROM ledger_entries WHERE wallet_id = 'W4004' AND transfer_rrn IS NULL);

INSERT INTO ledger_entries (wallet_id, transfer_rrn, entry_type, amount, created_at)
SELECT 'W5005', NULL, 'CREDIT', 1000.00, created_at FROM wallets
WHERE id = 'W5005' AND NOT EXISTS (SELECT 1 FROM ledger_entries WHERE wallet_id = 'W5005' AND transfer_rrn IS NULL);
