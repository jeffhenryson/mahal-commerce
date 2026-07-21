CREATE TABLE customer_notes (
    id         BIGSERIAL    PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    autor      VARCHAR(80)  NOT NULL,
    texto      TEXT         NOT NULL,
    criado_em  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_customer_notes_customer_id ON customer_notes (customer_id);
