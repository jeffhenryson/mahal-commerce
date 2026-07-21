ALTER TABLE customers ADD COLUMN estagio VARCHAR(20) NOT NULL DEFAULT 'NOVO_LEAD';

CREATE TABLE customer_stage_transitions (
    id               BIGSERIAL   PRIMARY KEY,
    customer_id      BIGINT      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    de               VARCHAR(20) NOT NULL,
    para             VARCHAR(20) NOT NULL,
    autor            VARCHAR(80) NOT NULL,
    transicionado_em TIMESTAMP   NOT NULL
);

CREATE INDEX idx_customer_stage_transitions_customer_id ON customer_stage_transitions (customer_id);
