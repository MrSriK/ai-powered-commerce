CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    shipping_name VARCHAR(255) NOT NULL,
    shipping_phone_number VARCHAR(15) NOT NULL,
    shipping_address_line_1 TEXT NOT NULL,
    shipping_address_line_2 TEXT,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL,
    postal_code VARCHAR(255) NOT NULL,
    country VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    total_items INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TRIGGER trg_orders_updated_at
  BEFORE UPDATE ON orders
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID,
    product_name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    sku VARCHAR(255) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE TRIGGER trg_order_items_updated_at
  BEFORE UPDATE ON order_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
