CREATE TABLE campaign_automations (
    id             BIGSERIAL    PRIMARY KEY,
    nome           VARCHAR(100) NOT NULL,
    gatilho        VARCHAR(20)  NOT NULL,
    segmento_alvo  VARCHAR(20)  NOT NULL,
    canal          VARCHAR(10)  NOT NULL,
    template       TEXT         NOT NULL,
    ativa          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMP    NOT NULL
);

CREATE TABLE campaign_log (
    id             BIGSERIAL    PRIMARY KEY,
    automation_id  BIGINT       NOT NULL REFERENCES campaign_automations(id) ON DELETE CASCADE,
    customer_id    BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    status         VARCHAR(30)  NOT NULL,
    disparado_em   TIMESTAMP    NOT NULL,
    convertido_em  TIMESTAMP
);

CREATE INDEX idx_campaign_log_automation_id ON campaign_log (automation_id);
