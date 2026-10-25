CREATE TABLE carts(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TRIGGER trg_carts_updated_at
  BEFORE UPDATE ON carts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE cart_items(
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_cart_id_product UNIQUE(cart_id, product_id),
    CHECK (quantity > 0)
);
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product ON cart_items(product_id);
CREATE TRIGGER trg_cart_items_updated_at
  BEFORE UPDATE ON cart_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();