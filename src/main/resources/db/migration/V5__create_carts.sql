CREATE TABLE carts(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE cart_items(
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_cart_id_product UNIQUE(cart_id, product_id),
    CHECK (quantity > 0)
);
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product ON cart_items(product_id);