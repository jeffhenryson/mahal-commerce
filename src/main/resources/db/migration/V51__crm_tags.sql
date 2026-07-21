CREATE TABLE tags (
    id   BIGSERIAL    PRIMARY KEY,
    nome VARCHAR(50)  NOT NULL,
    CONSTRAINT uk_tags_nome UNIQUE (nome)
);

CREATE TABLE customer_tags (
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    tag_id      BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (customer_id, tag_id)
);

CREATE INDEX idx_customer_tags_tag_id ON customer_tags (tag_id);
