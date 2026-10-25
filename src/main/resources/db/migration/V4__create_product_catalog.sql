CREATE TABLE categories(
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);
CREATE TRIGGER trg_categories_updated_at
  BEFORE UPDATE ON categories
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE products(
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    brand VARCHAR(100) NOT NULL,
    sku VARCHAR(255) UNIQUE NOT NULL,
    cost_price NUMERIC(10,2) NOT NULL,
    selling_price NUMERIC(10,2) NOT NULL,
    stock INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CHECK (stock >= 0),
    CHECK (cost_price >= 0),
    CHECK (selling_price >= 0)
);
CREATE INDEX idx_products_category ON products(category_id);
CREATE TRIGGER trg_products_updated_at
  BEFORE UPDATE ON products
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_images(
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    image_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_product_images_product_display_order UNIQUE(product_id, display_order)
);
CREATE INDEX idx_product_images_product ON product_images(product_id);
CREATE TRIGGER trg_product_images_updated_at
  BEFORE UPDATE ON product_images
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
