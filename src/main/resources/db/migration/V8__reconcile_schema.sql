-- V8: Schema reconciliation. Rationale for each change: see schema-decisions.md

-- 1. users: rows are provisioned from a JWT on first login, which cannot
--    guarantee names, email, or phone. UNIQUE on email still allows many NULLs.
ALTER TABLE users ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE users ALTER COLUMN last_name DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;

-- 2. addresses: the entity already requires a phone, and orders snapshot it as NOT NULL.
ALTER TABLE addresses ALTER COLUMN phone_number SET NOT NULL;

-- 3. Enum CHECK constraints (Java enums do not protect the database).
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'));
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

-- 4. Numeric CHECK constraints.
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0),
    ADD CONSTRAINT chk_orders_total_items CHECK (total_items > 0);
ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    ADD CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0);
ALTER TABLE product_images
    ADD CONSTRAINT chk_product_images_display_order CHECK (display_order >= 1);

-- 5. A transaction reference identifies exactly one order.
ALTER TABLE orders
    ADD CONSTRAINT uq_orders_transaction_id UNIQUE (transaction_id);

-- 6. One money precision everywhere: NUMERIC(12,2).
ALTER TABLE products ALTER COLUMN cost_price TYPE NUMERIC(12, 2);
ALTER TABLE products ALTER COLUMN selling_price TYPE NUMERIC(12, 2);
ALTER TABLE orders ALTER COLUMN total_amount TYPE NUMERIC(12, 2);
ALTER TABLE order_items ALTER COLUMN unit_price TYPE NUMERIC(12, 2);

-- 7. Missing indexes. Composite ones also serve the required sort orders.
CREATE INDEX idx_addresses_user ON addresses(user_id);
CREATE INDEX idx_ai_conversations_user_created ON ai_conversations(user_id, created_at DESC);
CREATE INDEX idx_ai_messages_conversation_created ON ai_messages(conversation_id, created_at);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC, id DESC);

-- 8. Redundant indexes (already covered by a UNIQUE constraint or the composite above).
DROP INDEX idx_orders_user;
DROP INDEX idx_cart_items_cart;
DROP INDEX idx_product_images_product;
