CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(15),
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE UNIQUE INDEX uq_default_address_per_user ON addresses(user_id) WHERE is_default = TRUE;

CREATE TRIGGER trg_addresses_updated_at
  BEFORE UPDATE ON addresses
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();