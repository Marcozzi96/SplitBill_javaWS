CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pairwise_group_debtor
    ON pairwise_settlements (group_id, debtor_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pairwise_group_creditor
    ON pairwise_settlements (group_id, creditor_id);
