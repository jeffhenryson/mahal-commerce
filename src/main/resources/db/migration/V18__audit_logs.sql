CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(80)  NOT NULL,
    action     VARCHAR(80)  NOT NULL,
    target     VARCHAR(255),
    details    TEXT,
    ip_address VARCHAR(45),
    timestamp  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_username  ON audit_logs (username);
CREATE INDEX idx_audit_logs_action    ON audit_logs (action);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
