-- V9: orders.shipped_at (decision D-80). Records when an order became SHIPPED so the
-- progression job can move it to DELIVERED after a delay. Set once, never changed (4.23).
--
-- Backfill: existing SHIPPED/DELIVERED rows get shipped_at = updated_at, the best
-- available ship time. Side effect: trg_orders_updated_at (BEFORE UPDATE) also moves
-- updated_at of those rows to the migration time. The assignment reads the OLD row's
-- updated_at, so shipped_at keeps the old estimate. Accepted; trigger left enabled.
-- CANCELLED orders keep shipped_at NULL (the old schema never recorded it).

ALTER TABLE orders ADD COLUMN shipped_at TIMESTAMPTZ NULL;

UPDATE orders SET shipped_at = updated_at
WHERE status IN ('SHIPPED', 'DELIVERED') AND shipped_at IS NULL;

-- Must come after the backfill, or it would fail on existing shipped rows.
ALTER TABLE orders ADD CONSTRAINT chk_orders_shipped_at_required
    CHECK (status NOT IN ('SHIPPED', 'DELIVERED') OR shipped_at IS NOT NULL);

-- Serves the progression job: PENDING, CONFIRMED, and SHIPPED with shipped_at <= cutoff.
CREATE INDEX idx_orders_progression ON orders (status, shipped_at)
    WHERE deleted_at IS NULL AND status IN ('PENDING', 'CONFIRMED', 'SHIPPED');
