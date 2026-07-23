CREATE TABLE supplier (
    id         BIGSERIAL      PRIMARY KEY,
    legal_name VARCHAR(150)   NOT NULL,
    tax_id     VARCHAR(20)    NOT NULL UNIQUE,
    email      VARCHAR(150),
    active     BOOLEAN        NOT NULL DEFAULT TRUE
);
