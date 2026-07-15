CREATE TABLE product (
    id       BIGSERIAL     PRIMARY KEY,
    sku      VARCHAR(50)   NOT NULL,
    name     VARCHAR(255)  NOT NULL,
    category VARCHAR(100),
    active   BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_sku UNIQUE (sku)
);

CREATE TABLE product_variant (
    id         BIGSERIAL   PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    sku        VARCHAR(50) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_variant_sku UNIQUE (sku)
);

CREATE INDEX idx_product_variant_product_id ON product_variant (product_id);

CREATE TABLE product_attribute (
    variant_id BIGINT       NOT NULL REFERENCES product_variant(id) ON DELETE CASCADE,
    attr_type  VARCHAR(50)  NOT NULL,
    attr_value VARCHAR(100) NOT NULL
);

CREATE INDEX idx_product_attribute_variant_id ON product_attribute (variant_id);
